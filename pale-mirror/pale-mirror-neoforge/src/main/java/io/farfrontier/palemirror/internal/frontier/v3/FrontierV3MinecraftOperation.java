package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.BooleanSupplier;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3OperationDiagnostics.Reason.*;

/** Common application boundary. Owners provide an already-admitted intent and physical
 * ports; this class cannot inspect a meal, harvest, recipe, job or cargo phase. */
final class FrontierV3MinecraftOperation {
    record Participant(FrontierV3OperationReceipt.Participant identity, CompoundTag persistent, Runnable dirty) {
        Participant { Objects.requireNonNull(identity); Objects.requireNonNull(persistent); Objects.requireNonNull(dirty); }
    }
    enum Status { APPLIED, OBSERVED, NOT_APPLIED, SOURCE_CHANGED, AMBIGUOUS, WAITING_DURABILITY, WAITING_PREPARATION }
    record Result(Status status, Optional<UUID> operation) {
        Result {
            Objects.requireNonNull(status); Objects.requireNonNull(operation);
            if ((status == Status.WAITING_DURABILITY || status == Status.WAITING_PREPARATION) != operation.isEmpty())
                throw new IllegalArgumentException("physical result has an invalid operation identity");
        }
        Result(Status status, UUID ticket) { this(status, Optional.of(ticket)); }
        UUID ticket() { return operation.orElseThrow(); }
        static Result waiting() { return new Result(Status.WAITING_DURABILITY, Optional.empty()); }
        static Result preparing() { return new Result(Status.WAITING_PREPARATION, Optional.empty()); }
        boolean pending() { return status == Status.WAITING_DURABILITY || status == Status.WAITING_PREPARATION; }
        boolean complete() { return status == Status.APPLIED || status == Status.OBSERVED; }
    }
    record Request(FrontierV3OperationReceipt.Declaration declaration, List<Participant> participants,
            BooleanSupplier before, FrontierV3NativeEffect effect, BooleanSupplier after) {
        Request {
            Objects.requireNonNull(declaration); participants = List.copyOf(participants);
            Objects.requireNonNull(before); Objects.requireNonNull(effect); Objects.requireNonNull(after);
            if (participants.stream().anyMatch(p -> p.identity().medium() == FrontierV3OperationReceipt.Medium.ENTITY_SNAPSHOT))
                throw new IllegalArgumentException("a saved observation is not a mutation port");
            var identities = participants.stream().map(Participant::identity).collect(java.util.stream.Collectors.toSet());
            if (identities.size() != participants.size() || !identities.equals(declaration.participants()))
                throw new IllegalArgumentException("physical ports do not cover the exact operation participants");
        }
    }
    private FrontierV3MinecraftOperation() { }
    /** A producer may cancel its exact durable action only while the live attempt
     * is still unused. Matching a native preimage after recovery is not evidence. */
    static Optional<FrontierV3OperationReceipt> cancelUnattempted(ServerLevel level,
            FrontierV3OperationReceipt.Declaration declaration) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("native cancellation outside server thread");
        var ledger = FrontierV3OperationLedger.get(level);
        if (!ledger.canPrepare(declaration)) {
            FrontierV3OperationDiagnostics.waiting(level, declaration, FrontierV3OperationDiagnostics.Backpressure.JOURNAL_CAPACITY);
            return Optional.empty();
        }
        var retained = ledger.find(declaration).orElse(null);
        if (retained != null && !retained.declaration().equals(declaration)) {
            FrontierV3OperationDiagnostics.result(level, retained, Status.AMBIGUOUS); return Optional.empty();
        }
        var operation = ledger.prepare(declaration);
        if (!ledger.sealUnattemptedCancellation(operation.ticket())) {
            FrontierV3OperationDiagnostics.result(level, operation, Status.AMBIGUOUS); return Optional.empty();
        }
        ledger.persist(level);
        return Optional.of(ledger.require(operation.ticket()));
    }
    /** Historical APPLIED is not evidence that all independent native files
     * survived a crash. Resolve only the missing participants, without loading
     * chunks, recreating entities, replaying effects or inventing compensation. */
    static boolean recoverOutcome(ServerLevel level, UUID ticket) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("native recovery outside server thread");
        var ledger = FrontierV3OperationLedger.get(level); var operation = ledger.require(ticket);
        var missing = ledger.unverifiedNative(ticket);
        if (missing.isEmpty()) return true;
        var ports = FrontierV3OperationParticipantLookup.current(level, missing);
        if (ports.isEmpty()) {
            FrontierV3OperationDiagnostics.waiting(level, operation.declaration(), FrontierV3OperationDiagnostics.Backpressure.NATIVE_RECOVERY_EVIDENCE);
            return false;
        }
        if (!ports.stream().map(Participant::identity).collect(java.util.stream.Collectors.toSet()).equals(missing)
                || !ports.stream().allMatch(port -> FrontierV3NativeOperationMarkers.contains(port.persistent(), operation.nativeReceipt(port.identity())))) {
            FrontierV3OperationDiagnostics.result(level, operation, Status.AMBIGUOUS); return false;
        }
        for (var port : ports) ledger.applied(operation.nativeReceipt(port.identity()));
        ledger.persist(level);
        FrontierV3OperationDiagnostics.result(level, ledger.require(ticket), Status.OBSERVED);
        return true;
    }
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
        if (retained.cancelled()) return Optional.of(FrontierV3OperationDiagnostics.result(level, retained, Status.NOT_APPLIED));
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
            List<Participant> participants, BooleanSupplier before, FrontierV3NativeEffect effect, BooleanSupplier after) {
        return applyBatch(level, List.of(new Request(declaration, participants, before, effect, after))).getFirst();
    }
    /** Disjoint effects share prepare/result force boundaries, not atomicity. An interrupted
     * batch retains exact per-operation evidence; it can never roll back or replay the batch. */
    static List<Result> applyBatch(ServerLevel level, List<Request> requests) {
        return applyBatch(level, requests, false);
    }
    static List<Result> applyBatchDeferred(ServerLevel level, List<Request> requests) {
        return applyBatch(level, requests, true);
    }
    /** The same native boundary, with async force receipts. A waiting result has
     * not invoked mutation; callers retain their live permission until it does. */
    static Result applyDeferred(ServerLevel level, Request request) {
        return applyBatch(level, List.of(request), true).getFirst();
    }
    static Result applyDeferred(ServerLevel level, FrontierV3OperationReceipt.Declaration declaration,
            List<Participant> participants, BooleanSupplier before, FrontierV3NativeEffect effect, BooleanSupplier after) {
        return applyDeferred(level, new Request(declaration, participants, before, effect, after));
    }
    private static List<Result> applyBatch(ServerLevel level, List<Request> requests, boolean deferred) {
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
            if (!ledger.isDirty() && ledger.durableReady() && pruned.add(participant.persistent())
                    && FrontierV3NativeOperationMarkers.prune(participant.persistent(), ledger))
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
                results.set(i, FrontierV3OperationDiagnostics.result(level, retained, Status.AMBIGUOUS, DECLARATION_CHANGED)); continue;
            }
            if (retained != null && retained.cancelled()) {
                results.set(i, FrontierV3OperationDiagnostics.result(level, retained, Status.NOT_APPLIED)); continue;
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
                boolean cancelled = deferred ? ledger.sealUnattemptedCancellation(retained.ticket())
                        : ledger.cancelUnattempted(retained.ticket());
                results.set(i, FrontierV3OperationDiagnostics.result(level, retained, cancelled ? Status.NOT_APPLIED : Status.AMBIGUOUS,
                        PRECONDITION_CHANGED));
            }
        }
        if (deferred) ledger.persistAsync(level); else ledger.persist(level);
        var attempts = new HashSet<Integer>();
        try {
            for (int i = 0; i < requests.size(); i++) if (results.get(i) == null) {
                var request = requests.get(i); var retained = prepared.get(i);
                boolean attempt = !markers(request, retained);
                if (attempt) {
                    if (deferred && ledger.unattempted(retained.ticket()) && !ledger.durableOperation(retained.ticket())) {
                        results.set(i, Result.waiting()); continue;
                    }
                    // Earlier effects can change a later precondition. No attempt
                    // has been consumed for this participant yet, so cancellation is provable.
                    if (!request.before().getAsBoolean()) {
                        boolean cancelled = deferred ? ledger.sealUnattemptedCancellation(retained.ticket())
                                : ledger.cancelUnattempted(retained.ticket());
                        results.set(i, FrontierV3OperationDiagnostics.result(level, retained,
                                cancelled ? Status.NOT_APPLIED : Status.AMBIGUOUS, PRECONDITION_CHANGED)); continue;
                    }
                    // The journal is durable now. Prepare immediately before the live
                    // attempt, so asynchronous preparation never spends its permission.
                    if (ledger.unattempted(retained.ticket())) {
                        var admission = Objects.requireNonNull(request.effect().preparation().get());
                        if (!admission.ready()) {
                            results.set(i, FrontierV3OperationDiagnostics.waiting(level, request.declaration(),
                                    FrontierV3OperationDiagnostics.Backpressure.EFFECT_PREPARATION, admission.waitingFor().orElseThrow()));
                            continue;
                        }
                    }
                    // Preparation can admit a competing canonical/native transition.
                    if (!request.before().getAsBoolean()) {
                        boolean cancelled = ledger.sealUnattemptedCancellation(retained.ticket());
                        results.set(i, FrontierV3OperationDiagnostics.result(level, retained,
                                cancelled ? Status.NOT_APPLIED : Status.AMBIGUOUS, PRECONDITION_CHANGED)); continue;
                    }
                    if (!ledger.beginAttempt(retained.ticket())) {
                        results.set(i, FrontierV3OperationDiagnostics.result(level, retained, Status.AMBIGUOUS,
                                NO_LIVE_ATTEMPT_PERMISSION)); continue;
                    }
                    // Never consume the rest of the batch before this callback runs.
                    attempts.add(i);
                }
                if (attempt) {
                    FrontierV3NativeEffect.Outcome outcome;
                    try { outcome = Objects.requireNonNull(request.effect().mutation().get(), "missing native outcome"); }
                    catch (RuntimeException | Error failure) {
                        FrontierV3OperationDiagnostics.result(level, retained, Status.AMBIGUOUS, EFFECT_EXCEPTION);
                        throw failure;
                    }
                    if (outcome != FrontierV3NativeEffect.Outcome.APPLIED) {
                        boolean refused = outcome == FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT
                                && ledger.rejectCurrentAttempt(retained.ticket());
                        results.set(i, FrontierV3OperationDiagnostics.result(level, ledger.require(retained.ticket()),
                                refused ? Status.NOT_APPLIED : Status.AMBIGUOUS,
                                refused ? REFUSED_BEFORE_EFFECT : outcome == FrontierV3NativeEffect.Outcome.UNKNOWN
                                        ? EFFECT_OUTCOME_UNKNOWN : REFUSAL_NOT_SEALED)); continue;
                    }
                }
                if (!request.after().getAsBoolean()) {
                    results.set(i, FrontierV3OperationDiagnostics.result(level, retained, attempt ? Status.AMBIGUOUS : Status.SOURCE_CHANGED,
                            POSTCONDITION_MISMATCH)); continue;
                }
                for (var participant : request.participants()) {
                    var receipt = retained.nativeReceipt(participant.identity());
                    if (attempt) { FrontierV3NativeOperationMarkers.stamp(participant.persistent(), receipt); participant.dirty().run(); }
                    ledger.applied(receipt);
                }
                results.set(i, FrontierV3OperationDiagnostics.result(level, retained, attempt ? Status.APPLIED : Status.OBSERVED));
            }
        } finally {
            for (int index : attempts) ledger.finishAttempt(prepared.get(index).ticket());
            if (deferred) ledger.persistAsync(level); else ledger.persist(level);
        }
        return List.copyOf(results);
    }
    private static boolean markers(Request request, FrontierV3OperationReceipt retained) {
        return request.participants().stream().allMatch(participant -> FrontierV3NativeOperationMarkers.contains(
                participant.persistent(), retained.nativeReceipt(participant.identity())));
    }
}
