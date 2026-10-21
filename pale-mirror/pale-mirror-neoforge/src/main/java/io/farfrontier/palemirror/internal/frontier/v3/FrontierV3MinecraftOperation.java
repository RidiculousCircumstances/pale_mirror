package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Common application boundary. Owners provide an already-admitted intent and physical
 * ports; this class cannot inspect a meal, harvest, recipe, job or cargo phase. */
final class FrontierV3MinecraftOperation {
    record Participant(FrontierV3OperationReceipt.Participant identity, CompoundTag persistent, Runnable dirty) {
        Participant { Objects.requireNonNull(identity); Objects.requireNonNull(persistent); Objects.requireNonNull(dirty); }
    }
    enum Status { APPLIED, OBSERVED, NOT_APPLIED, SOURCE_CHANGED, AMBIGUOUS, WAITING_DURABILITY }
    record Result(Status status, Optional<UUID> operation) {
        Result {
            Objects.requireNonNull(status); Objects.requireNonNull(operation);
            if ((status == Status.WAITING_DURABILITY) != operation.isEmpty())
                throw new IllegalArgumentException("physical result has an invalid operation identity");
        }
        Result(Status status, UUID ticket) { this(status, Optional.of(ticket)); }
        UUID ticket() { return operation.orElseThrow(); }
        static Result waiting() { return new Result(Status.WAITING_DURABILITY, Optional.empty()); }
        boolean complete() { return status == Status.APPLIED || status == Status.OBSERVED; }
    }
    record Request(FrontierV3OperationReceipt.Declaration declaration, List<Participant> participants,
            BooleanSupplier before, BooleanSupplier mutation, BooleanSupplier after) {
        Request {
            Objects.requireNonNull(declaration); participants = List.copyOf(participants);
            Objects.requireNonNull(before); Objects.requireNonNull(mutation); Objects.requireNonNull(after);
            var identities = participants.stream().map(Participant::identity).collect(java.util.stream.Collectors.toSet());
            if (identities.size() != participants.size() || !identities.equals(declaration.participants()))
                throw new IllegalArgumentException("physical ports do not cover the exact operation participants");
        }
    }
    private FrontierV3MinecraftOperation() { }
    /** Recovery may inspect exact receipts without granting another mutation attempt. */
    static Optional<Result> observe(ServerLevel level, FrontierV3OperationReceipt.Declaration declaration,
            List<Participant> participants, BooleanSupplier after) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("native recovery outside server thread");
        var ledger = FrontierV3OperationLedger.get(level); var retained = ledger.find(declaration).orElse(null);
        if (retained == null) return Optional.empty();
        if (participants.size() != declaration.participants().size()
                || !participants.stream().map(Participant::identity).collect(java.util.stream.Collectors.toSet()).equals(declaration.participants()))
            throw new IllegalArgumentException("native recovery has foreign participants or intent");
        if (!retained.declaration().equals(declaration))
            return Optional.of(FrontierV3OperationDiagnostics.result(level, retained, Status.AMBIGUOUS));
        if (!participants.stream().allMatch(p -> FrontierV3NativeOperationMarkers.contains(p.persistent(), retained.nativeReceipt(p.identity()))))
            return Optional.of(FrontierV3OperationDiagnostics.result(level, retained, Status.AMBIGUOUS));
        if (!after.getAsBoolean()) return Optional.of(FrontierV3OperationDiagnostics.result(level, retained, Status.SOURCE_CHANGED));
        for (var participant : participants) ledger.applied(retained.nativeReceipt(participant.identity()));
        ledger.persist(level);
        return Optional.of(FrontierV3OperationDiagnostics.result(level, retained, Status.OBSERVED));
    }
    static FrontierV3OperationReceipt.Declaration declaration(FrontierV3OperationReceipt.Protocol protocol,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId owner,
            io.farfrontier.palemirror.frontier.v3.api.FrontierPayload prepared, List<Participant> participants) {
        String fingerprint = FrontierV3OperationConfirmation.fingerprint(prepared);
        return new FrontierV3OperationReceipt.Declaration(protocol, owner, fingerprint, fingerprint,
                participants.stream().map(Participant::identity).collect(java.util.stream.Collectors.toSet()));
    }
    static Result apply(ServerLevel level, FrontierV3OperationReceipt.Declaration declaration,
            List<Participant> participants, BooleanSupplier before, BooleanSupplier mutation, BooleanSupplier after) {
        return applyBatch(level, List.of(new Request(declaration, participants, before, mutation, after))).getFirst();
    }
    /** Disjoint effects share prepare/result force boundaries, not atomicity. An interrupted
     * batch retains exact per-operation evidence; it can never roll back or replay the batch. */
    static List<Result> applyBatch(ServerLevel level, List<Request> requests) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("native operation outside server thread");
        if (requests.isEmpty() || requests.size() > 256) throw new IllegalArgumentException("unbounded native operation batch");
        var all = new HashSet<FrontierV3OperationReceipt.Participant>();
        for (var request : requests) for (var participant : request.participants())
            if (!all.add(participant.identity())) throw new IllegalArgumentException("native batch has overlapping effects");
        var ledger = FrontierV3OperationLedger.get(level);
        var results = new ArrayList<Result>(Collections.nCopies(requests.size(), null));
        var prepared = new ArrayList<FrontierV3OperationReceipt>(Collections.nCopies(requests.size(), null));
        var additions = new IdentityHashMap<CompoundTag, Integer>();
        var pruned = Collections.newSetFromMap(new IdentityHashMap<CompoundTag, Boolean>());
        for (var request : requests) for (var participant : request.participants())
            if (pruned.add(participant.persistent()) && FrontierV3NativeOperationMarkers.prune(participant.persistent(), ledger))
                participant.dirty().run();
        for (int i = 0; i < requests.size(); i++) {
            var request = requests.get(i);
            if (!ledger.canPrepare(request.declaration())) {
                results.set(i, FrontierV3OperationDiagnostics.waiting(level, request.declaration(),
                        FrontierV3OperationDiagnostics.Backpressure.JOURNAL_CAPACITY)); continue;
            }
            var retained = ledger.find(request.declaration()).orElse(null);
            // A replaced/differently saved native incarnation is a local recovery case,
            // not permission to apply the old action to the new body/container/chunk.
            if (retained != null && !retained.declaration().equals(request.declaration())) {
                results.set(i, FrontierV3OperationDiagnostics.result(level, retained, Status.AMBIGUOUS)); continue;
            }
            var needed = new IdentityHashMap<CompoundTag, Integer>();
            for (var participant : request.participants())
                if (retained == null || !FrontierV3NativeOperationMarkers.contains(participant.persistent(), retained.nativeReceipt(participant.identity())))
                    needed.merge(participant.persistent(), 1, Integer::sum);
            if (needed.entrySet().stream().anyMatch(entry -> !FrontierV3NativeOperationMarkers.canStamp(entry.getKey(),
                    additions.getOrDefault(entry.getKey(), 0) + entry.getValue()))) {
                results.set(i, FrontierV3OperationDiagnostics.waiting(level, request.declaration(),
                        FrontierV3OperationDiagnostics.Backpressure.NATIVE_RECEIPT_CAPACITY)); continue;
            }
            needed.forEach((bag, count) -> additions.merge(bag, count, Integer::sum));
            retained = ledger.prepare(request.declaration()); prepared.set(i, retained);
            boolean observed = markers(request, retained);
            if (!observed && !request.before().getAsBoolean()) {
                boolean cancelled = ledger.cancelUnattempted(retained.ticket());
                results.set(i, FrontierV3OperationDiagnostics.result(level, retained, cancelled ? Status.NOT_APPLIED : Status.AMBIGUOUS));
            }
        }
        ledger.persist(level); // Every admitted intent is durable before the first mutation.
        var attempts = new HashSet<Integer>();
        for (int i = 0; i < requests.size(); i++) if (results.get(i) == null && !markers(requests.get(i), prepared.get(i))) {
            if (ledger.beginAttempt(prepared.get(i).ticket())) attempts.add(i);
            else results.set(i, FrontierV3OperationDiagnostics.result(level, prepared.get(i), Status.AMBIGUOUS));
        }
        try {
            for (int i = 0; i < requests.size(); i++) if (results.get(i) == null) {
                var request = requests.get(i); var retained = prepared.get(i);
                boolean attempt = attempts.contains(i);
                if (attempt && (!request.before().getAsBoolean() || !request.mutation().getAsBoolean())) {
                    results.set(i, FrontierV3OperationDiagnostics.result(level, retained, Status.AMBIGUOUS)); continue;
                }
                if (!request.after().getAsBoolean()) {
                    results.set(i, FrontierV3OperationDiagnostics.result(level, retained, attempt ? Status.AMBIGUOUS : Status.SOURCE_CHANGED)); continue;
                }
                for (var participant : request.participants()) {
                    var receipt = retained.nativeReceipt(participant.identity());
                    if (attempt) { FrontierV3NativeOperationMarkers.stamp(participant.persistent(), receipt); participant.dirty().run(); }
                    ledger.applied(receipt);
                }
                results.set(i, FrontierV3OperationDiagnostics.result(level, retained, attempt ? Status.APPLIED : Status.OBSERVED));
            }
        } finally { ledger.persist(level); }
        return List.copyOf(results);
    }
    private static boolean markers(Request request, FrontierV3OperationReceipt retained) {
        return request.participants().stream().allMatch(participant -> FrontierV3NativeOperationMarkers.contains(
                participant.persistent(), retained.nativeReceipt(participant.identity())));
    }
}
