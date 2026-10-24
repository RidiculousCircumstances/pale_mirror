package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Retains unresolved physical obligations, not business jobs or a second resource ledger.
 * Each producer remains responsible for validating its exact CURRENT canonical intent. */
final class FrontierV3OperationLedger extends FrontierV3JournaledSavedData {
    private static final int MAX_PENDING = 8192;
    private static final int MAX_OWNER_CONTEXT_BYTES = 16 * 1024 * 1024;
    static final int MAX_NATIVE_RECEIPTS = 16384;
    private int pendingParticipants;
    private int pendingOwnerContextBytes;
    private record Key(FrontierV3OperationReceipt.Protocol protocol, io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, String operation) {
        static Key of(FrontierV3OperationReceipt.Declaration declaration) {
            return new Key(declaration.protocol(), declaration.owner(), declaration.operation());
        }
    }
    private final Map<UUID, FrontierV3OperationReceipt> operations;
    private final Map<Long, Long> retired;
    private final FrontierV3RetiredOperationRanges retiredIndex;
    static final class OwnerCommand {
        enum Closure { AUTOMATIC, EXPLICIT_OWNER }
        private final io.farfrontier.palemirror.frontier.v3.api.CommandId id;
        private final io.farfrontier.palemirror.frontier.v3.api.FrontierPayload consequence;
        private final String fingerprint;
        private final Closure closure;
        private final byte[] context;
        OwnerCommand(io.farfrontier.palemirror.frontier.v3.api.CommandId id,
                io.farfrontier.palemirror.frontier.v3.api.FrontierPayload consequence) {
            this(id, consequence, Closure.AUTOMATIC, new byte[0]);
        }
        OwnerCommand(io.farfrontier.palemirror.frontier.v3.api.CommandId id,
                io.farfrontier.palemirror.frontier.v3.api.FrontierPayload consequence, Closure closure, byte[] context) {
            this.id = Objects.requireNonNull(id); this.consequence = Objects.requireNonNull(consequence);
            this.closure = Objects.requireNonNull(closure); this.context = Objects.requireNonNull(context).clone();
            if (context.length > 262144) throw new IllegalArgumentException("unbounded physical owner context");
            if (!consequence.requiresDurableBeforeEffect()) throw new IllegalArgumentException("native consequence must commit durably");
            fingerprint = FrontierV3OperationConfirmation.fingerprint(consequence);
        }
        io.farfrontier.palemirror.frontier.v3.api.CommandId id() { return id; }
        io.farfrontier.palemirror.frontier.v3.api.FrontierPayload consequence() { return consequence; }
        String fingerprint() { return fingerprint; }
        Closure closure() { return closure; }
        byte[] context() { return context.clone(); }
        int contextSize() { return context.length; }
        @Override public boolean equals(Object other) {
            return other instanceof OwnerCommand value && id.equals(value.id) && fingerprint.equals(value.fingerprint)
                    && consequence.equals(value.consequence) && closure == value.closure && Arrays.equals(context, value.context);
        }
        @Override public int hashCode() { return Objects.hash(id, fingerprint, closure, Arrays.hashCode(context)); }
    }
    private final Map<UUID, OwnerCommand> ownerCommands;
    /** Positive unused child permissions consumed by a declared enclosing operation.
     * Unlike a recovered PREPARED row, this is durable proof that the child was
     * excluded from native execution before its parent's native boundary. */
    private final Map<UUID, UUID> cancellationParents;
    private final Map<UUID, Integer> cancellationChildren = new HashMap<>();
    private long nextSequence;
    private final Map<Key, UUID> byOwner = new HashMap<>();
    /** Derived, bounded recovery index; never a second durable operation owner. */
    private final Map<FrontierV3OperationReceipt.Protocol, LinkedHashSet<UUID>> byProtocol = new EnumMap<>(FrontierV3OperationReceipt.Protocol.class);
    private final Set<UUID> unattempted = new HashSet<>();
    private final Set<UUID> attempting = new HashSet<>();
    private final Set<UUID> changedOperations = new HashSet<>();
    private final Map<UUID, java.util.concurrent.CompletableFuture<Long>> writeReceipts = new HashMap<>();
    @Override protected void submitted(java.util.concurrent.CompletableFuture<Long> receipt) {
        for (var ticket : changedOperations) writeReceipts.put(ticket, receipt);
        changedOperations.clear();
    }
    // A persisted APPLIED row records history, not the native image recovered by
    // this process. Only actual live receipts or acknowledged disk saves grant
    // confirmation; this bounded cache intentionally starts empty on recovery.
    private final Map<UUID, Set<FrontierV3OperationReceipt.Participant>> currentNative = new HashMap<>();
    private final Map<UUID, Long> observedSince = new HashMap<>(); // diagnostics only, reset explicitly on process recovery
    private FrontierV3OperationLedger(Map<UUID, FrontierV3OperationReceipt> initial,
            Map<Long, Long> ranges, Map<UUID, OwnerCommand> commands, Map<UUID, UUID> cancellations, long nextSequence) {
        super(FrontierV3PhysicalStoreKind.OPERATIONS);
        if (initial.size() > MAX_PENDING) throw new IllegalArgumentException("physical operation backlog exceeds its bound");
        operations = table("operations", initial, UUID::toString, (key, value) -> FrontierV3OperationReceiptCodec.write(value));
        retired = table("retired", ranges, Object::toString, FrontierV3OperationLedger::rangeTag);
        retiredIndex = new FrontierV3RetiredOperationRanges(retired,
                initial.values().stream().map(FrontierV3OperationReceipt::sequence).toList(), nextSequence);
        if (!initial.keySet().containsAll(commands.keySet()) || commands.values().stream().map(OwnerCommand::id).distinct().count() != commands.size())
            throw new IllegalArgumentException("owner command has no unique operation");
        for (var entry : commands.entrySet()) {
            var value = initial.get(entry.getKey());
            if (!value.cancelled() && !value.applied().equals(value.declaration().participants())
                    && !entry.getValue().fingerprint().equals(value.declaration().fingerprint()))
                throw new IllegalArgumentException("prepared owner intent differs from its physical declaration");
            pendingOwnerContextBytes = Math.addExact(pendingOwnerContextBytes, entry.getValue().contextSize());
            if (pendingOwnerContextBytes > MAX_OWNER_CONTEXT_BYTES)
                throw new IllegalArgumentException("physical owner context backlog exceeds its byte bound");
        }
        ownerCommands = table("ownerCommands", commands, UUID::toString, FrontierV3OperationLedger::commandTag);
        cancellationParents = table("cancellationParents", cancellations, UUID::toString, FrontierV3OperationLedger::cancellationTag);
        cancellations.values().forEach(parent -> cancellationChildren.merge(parent, 1, Math::addExact));
        cancellations.forEach((child, parent) -> validateCancellation(parent, child));
        if (nextSequence < 1 || initial.values().stream().map(FrontierV3OperationReceipt::sequence).distinct().count() != initial.size())
            throw new IllegalArgumentException("invalid physical operation causal order");
        this.nextSequence = nextSequence;
        initial.forEach((ticket, value) -> {
            observedSince.put(ticket, System.nanoTime());
            if (!ticket.equals(value.ticket()) || byOwner.putIfAbsent(Key.of(value.declaration()), ticket) != null)
                throw new IllegalArgumentException("competing recovered physical operation");
            byProtocol.computeIfAbsent(value.declaration().protocol(), ignored -> new LinkedHashSet<>()).add(ticket);
            if (value.sequence() >= nextSequence) throw new IllegalArgumentException("physical operation beyond issued sequence");
            pendingParticipants = Math.addExact(pendingParticipants, value.declaration().participants().size());
        });
        if (pendingParticipants > MAX_NATIVE_RECEIPTS) throw new IllegalArgumentException("physical participant backlog exceeds admission bound");
    }
    static FrontierV3OperationLedger inMemory() { return new FrontierV3OperationLedger(Map.of(), Map.of(), Map.of(), Map.of(), 1); }
    static FrontierV3OperationLedger get(ServerLevel level) {
        return FrontierV3JournaledSavedData.get(level, FrontierV3PhysicalStoreKind.OPERATIONS,
                FrontierV3OperationLedger::inMemory, FrontierV3OperationLedger::load);
    }
    /** Called immediately before physical application, AFTER the owner's durable preparation.
     * A returned existing entry is observation/recovery, never a new execution permission. */
    FrontierV3OperationReceipt prepare(FrontierV3OperationReceipt.Declaration declaration) {
        var id = byOwner.get(Key.of(declaration));
        if (id != null) {
            var prior = operations.get(id);
            if (!prior.declaration().equals(declaration)) throw new IllegalArgumentException("physical intent changed under the same owner operation");
            return prior;
        }
        if (!canPrepare(declaration)) throw new IllegalStateException("physical operation backlog needs save/recovery; no evidence may be evicted");
        var value = FrontierV3OperationReceipt.prepare(declaration, nextSequence);
        nextSequence = Math.incrementExact(nextSequence);
        operations.put(value.ticket(), value); byOwner.put(Key.of(declaration), value.ticket());
        byProtocol.computeIfAbsent(declaration.protocol(), ignored -> new LinkedHashSet<>()).add(value.ticket());
        pendingParticipants += declaration.participants().size();
        observedSince.put(value.ticket(), System.nanoTime());
        if (declaration.participants().stream().noneMatch(p -> p.medium() == FrontierV3OperationReceipt.Medium.ENTITY_SNAPSHOT))
            unattempted.add(value.ticket());
        changedOperations.add(value.ticket()); setDirty(); return value;
    }
    boolean canPrepare(FrontierV3OperationReceipt.Declaration declaration) {
        return byOwner.containsKey(Key.of(declaration)) || operations.size() < MAX_PENDING
                && pendingParticipants <= MAX_NATIVE_RECEIPTS - declaration.participants().size();
    }
    Optional<FrontierV3OperationReceipt> find(FrontierV3OperationReceipt.Declaration declaration) {
        return find(declaration.protocol(), declaration.owner(), declaration.operation());
    }
    Optional<FrontierV3OperationReceipt> find(FrontierV3OperationReceipt.Protocol protocol,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, String operation) {
        var ticket = byOwner.get(new Key(protocol, owner, operation));
        return ticket == null ? Optional.empty() : Optional.of(require(ticket));
    }
    /** Must follow persist(). Even matching preimages do not permit replay after restart. */
    boolean beginAttempt(UUID ticket) {
        require(ticket);
        if (!unattempted.contains(ticket)) return false;
        if (!durableOperation(ticket)) throw new IllegalStateException("physical operation has no durable prepare receipt");
        unattempted.remove(ticket);
        attempting.add(ticket); return true;
    }
    boolean durableOperation(UUID ticket) {
        require(ticket);
        if (changedOperations.contains(ticket)) return false;
        var receipt = writeReceipts.get(ticket);
        if (receipt == null) return true; // Recovered from a validated durable journal prefix.
        if (!receipt.isDone()) return false;
        receipt.join(); return true;
    }
    boolean unattempted(UUID ticket) { require(ticket); return unattempted.contains(ticket); }
    /** Read-only recovery classification, never permission to retry an unobserved effect. */
    boolean unresolvedAttempt(UUID ticket) {
        var value = require(ticket);
        return !value.cancelled() && !value.ownerClosed() && !value.applied().equals(value.declaration().participants())
                && !unattempted.contains(ticket) && !attempting.contains(ticket);
    }
    /** Explicit causal dependency supplied by the owning producer, not inferred
     * from overlapping native media. A body fatality may exclude a terrain-only
     * or station-only action. Capture in the same server turn before either
     * attempt; no old permit is reconstructed. */
    void deferCancellation(UUID parent, UUID child) {
        validateCancellation(parent, child);
        if (Objects.equals(cancellationParents.get(child), parent)) return;
        if (!unattempted.contains(parent) || !unattempted.remove(child))
            throw new IllegalStateException("conditional cancellation requires both original unused live permits");
        cancellationParents.put(child, parent); cancellationChildren.merge(parent, 1, Math::addExact);
        changedOperations.add(parent); changedOperations.add(child); setDirty();
    }
    private void validateCancellation(UUID parent, UUID child) {
        var enclosing = require(parent); var excluded = require(child); var owner = ownerCommands.get(parent);
        if (parent.equals(child) || enclosing.cancelled() || enclosing.ownerClosed()
                || excluded.cancelled() || excluded.ownerClosed() || !excluded.applied().isEmpty()
                || owner == null || owner.closure() != OwnerCommand.Closure.EXPLICIT_OWNER
                || owner.contextSize() == 0 || cancellationParents.containsKey(parent)
                || cancellationChildren.containsKey(child)
                || cancellationParents.containsKey(child) && !cancellationParents.get(child).equals(parent))
            throw new IllegalArgumentException("conditional cancellation lacks its exact retained parent/unused child declaration");
    }
    /** Only the enclosing live native callback may supply a positive refusal.
     * Recovery, a timeout or matching item counts cannot grant this transition. */
    boolean rejectCurrentAttempt(UUID ticket) {
        var value = require(ticket);
        if (cancellationChildren.containsKey(ticket)) throw new IllegalStateException("enclosing native refusal requires its declared child recovery");
        if (!attempting.contains(ticket) || !value.applied().isEmpty()) return false;
        replace(value.cancel()); attempting.remove(ticket); return true;
    }
    void finishAttempt(UUID ticket) { attempting.remove(ticket); }
    FrontierV3OperationReceipt require(UUID ticket) {
        var value = operations.get(ticket);
        if (value == null) throw new IllegalArgumentException("unknown or retired physical operation ticket");
        return value;
    }
    Optional<FrontierV3OperationReceipt> find(UUID ticket) { return Optional.ofNullable(operations.get(ticket)); }
    /** Fair bounded probes. Unresolved evidence cannot starve a later operation. */
    List<FrontierV3OperationReceipt> probe(FrontierV3OperationReceipt.Protocol protocol, int limit) {
        if (limit < 1 || limit > 64) throw new IllegalArgumentException("invalid operation recovery budget");
        var index = byProtocol.get(protocol);
        if (index == null || index.isEmpty()) return List.of();
        var tickets = index.stream().limit(limit).toList();
        var result = new ArrayList<FrontierV3OperationReceipt>();
        for (var ticket : tickets) {
            index.remove(ticket); index.add(ticket);
            var operation = require(ticket);
            if (!operation.ownerClosed()) result.add(operation);
        }
        return List.copyOf(result);
    }
    Map<UUID, OwnerCommand> ownerCommands() { return Map.copyOf(ownerCommands); }
    Optional<OwnerCommand> ownerCommand(UUID ticket) { require(ticket); return Optional.ofNullable(ownerCommands.get(ticket)); }
    /** Only the confirmation coordinator may supersede an unaccepted, pinned command.
     * The physical effect and its ticket remain unchanged. */
    void replaceUnacceptedOwnerCommand(UUID ticket, OwnerCommand expected, OwnerCommand successor) {
        var value = require(ticket);
        if (value.ownerClosed() || !value.cancelled() && !value.applied().equals(value.declaration().participants())
                || !Objects.equals(ownerCommands.get(ticket), expected) || expected.id().equals(successor.id())
                || expected.closure() != successor.closure() || !Arrays.equals(expected.context, successor.context))
            throw new IllegalArgumentException("stale or incomplete operation recovery confirmation");
        ownerCommands.put(ticket, successor); changedOperations.add(ticket); setDirty();
    }
    void bindOwnerCommand(UUID ticket, OwnerCommand command) {
        var value = require(ticket);
        if (!value.cancelled() && !value.applied().equals(value.declaration().participants())) throw new IllegalStateException("owner command precedes applied participants or sealed cancellation");
        retainCommand(ticket, command);
    }
    /** Persist the producer's exact consequence BEFORE a non-replayable effect.
     * This is an outbox intent, not acknowledgement or permission to submit it.
     * Confirmation still requires positive evidence for every native participant. */
    void retainOwnerIntent(UUID ticket, OwnerCommand command) {
        var value = require(ticket);
        if (!value.declaration().fingerprint().equals(command.fingerprint()))
            throw new IllegalArgumentException("owner intent differs from its physical declaration");
        retainCommand(ticket, command);
    }
    private void retainCommand(UUID ticket, OwnerCommand command) {
        var prior = ownerCommands.get(ticket);
        if (prior != null && !prior.equals(command)) throw new IllegalArgumentException("owner confirmation changed payload/command identity");
        if (prior == null) {
            if (pendingOwnerContextBytes > MAX_OWNER_CONTEXT_BYTES - command.contextSize())
                throw new IllegalStateException("physical owner evidence backlog needs recovery before another non-replayable effect");
            pendingOwnerContextBytes += command.contextSize();
            ownerCommands.put(ticket, command); changedOperations.add(ticket); setDirty();
        }
    }
    void applied(FrontierV3OperationReceipt.NativeReceipt receipt) {
        if (cancellationParents.containsKey(receipt.ticket())) throw new IllegalStateException("excluded child acquired an impossible native effect");
        replace(require(receipt.ticket()).applied(receipt));
        currentNative.computeIfAbsent(receipt.ticket(), ignored -> new HashSet<>()).add(receipt.participant());
    }
    void saved(FrontierV3OperationReceipt.NativeReceipt receipt) {
        if (cancellationParents.containsKey(receipt.ticket())) throw new IllegalStateException("excluded child acquired an impossible native save");
        replace(require(receipt.ticket()).saved(receipt));
    }
    Set<FrontierV3OperationReceipt.Participant> unverifiedNative(UUID ticket) {
        var value = require(ticket);
        if (value.cancelled()) return Set.of();
        var missing = new HashSet<>(value.declaration().participants());
        missing.removeAll(value.saved()); missing.removeAll(currentNative.getOrDefault(ticket, Set.of()));
        return Set.copyOf(missing);
    }
    boolean nativeOutcomeAvailable(UUID ticket) { return unverifiedNative(ticket).isEmpty(); }
    void closeOwner(UUID ticket) {
        if (cancellationChildren.containsKey(ticket)) throw new IllegalStateException("operation retains conditional child obligations");
        replace(require(ticket).closeOwner());
    }
    private void replace(FrontierV3OperationReceipt value) {
        if (!value.equals(require(value.ticket()))) { operations.put(value.ticket(), value); changedOperations.add(value.ticket()); setDirty(); }
    }
    /** No TTL. The caller removes same-object markers only after this removal is durable.
     * Domain closure prohibits further callbacks; a retired random ticket is never recreated. */
    void retire(UUID ticket) {
        var value = require(ticket);
        if (!value.retirable()) throw new IllegalStateException("operation still owns canonical or native-save obligations");
        remove(value);
    }
    boolean cancelUnattempted(UUID ticket) {
        var value = require(ticket);
        if (!unattempted.contains(ticket) || !value.applied().isEmpty() || cancellationChildren.containsKey(ticket)) return false;
        remove(value); return true;
    }
    /** Cancellation with a canonical consequence must survive until that owner
     * acknowledges it. Recovered PREPARED is deliberately not an unattempted proof. */
    boolean sealUnattemptedCancellation(UUID ticket) {
        var value = require(ticket);
        if (value.cancelled()) return true;
        var parent = cancellationParents.get(ticket);
        if (parent != null) {
            var enclosing = require(parent);
            if (enclosing.cancelled() || !nativeOutcomeAvailable(parent)
                    || !enclosing.applied().equals(enclosing.declaration().participants())) return false;
            replace(value.cancel()); cancellationParents.remove(ticket);
            cancellationChildren.compute(parent, (key, count) -> count == 1 ? null : count - 1);
            changedOperations.add(parent); setDirty(); return true;
        }
        if (!unattempted.contains(ticket) || !value.applied().isEmpty()) return false;
        replace(value.cancel()); unattempted.remove(ticket); return true;
    }
    private void remove(FrontierV3OperationReceipt value) {
        UUID ticket = value.ticket();
        if (cancellationChildren.containsKey(ticket) || cancellationParents.containsKey(ticket))
            throw new IllegalStateException("conditional operation cannot retire its causal boundary");
        changedOperations.remove(ticket); writeReceipts.remove(ticket);
        attempting.remove(ticket);
        currentNative.remove(ticket);
        observedSince.remove(ticket);
        pendingParticipants -= value.declaration().participants().size();
        operations.remove(ticket); byOwner.remove(Key.of(value.declaration()), ticket);
        var command = ownerCommands.remove(ticket);
        if (command != null) pendingOwnerContextBytes -= command.contextSize();
        unattempted.remove(ticket); setDirty();
        var index = byProtocol.get(value.declaration().protocol());
        index.remove(ticket); if (index.isEmpty()) byProtocol.remove(value.declaration().protocol());
        retiredIndex.add(value.sequence());
    }
    boolean prunable(FrontierV3OperationReceipt.NativeReceipt receipt) {
        return !isDirty() && durableReady() && !operations.containsKey(receipt.ticket()) && retiredIndex.contains(receipt.sequence());
    }
    int pendingCount() { return operations.size(); }
    @Override protected String ownerDiagnostic() {
        long now = System.nanoTime();
        long age = observedSince.values().stream().mapToLong(since -> Math.max(0, now - since)).max().orElse(0) / 1_000_000;
        long nativeWaiting = operations.values().stream().filter(value -> !value.cancelled() && !value.saved().equals(value.declaration().participants())).count();
        long ownerWaiting = operations.values().stream().filter(value -> !value.ownerClosed()).count();
        return "{\"pendingOperations\":" + operations.size() + ",\"pendingParticipants\":" + pendingParticipants
                + ",\"ownerContextBytes\":" + pendingOwnerContextBytes
                + ",\"awaitingNativeSave\":" + nativeWaiting + ",\"awaitingOwner\":" + ownerWaiting
                + ",\"oldestObservedAgeMs\":" + age + ",\"ageScope\":\"current-process\",\"retiredRanges\":" + retired.size() + "}";
    }
    @Override protected CompoundTag metadata() { var tag = new CompoundTag(); tag.putInt("format", 5); tag.putLong("nextSequence", nextSequence); return tag; }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.merge(metadata()); var rows = new ListTag();
        operations.values().stream().sorted(Comparator.comparing(value -> value.ticket().toString()))
                .map(FrontierV3OperationReceiptCodec::write).forEach(rows::add); tag.put("operations", rows);
        var ranges = new ListTag(); retired.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> rangeTag(entry.getKey(), entry.getValue())).forEach(ranges::add);
        tag.put("retired", ranges);
        var commands = new ListTag(); ownerCommands.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> commandTag(entry.getKey(), entry.getValue())).forEach(commands::add);
        tag.put("ownerCommands", commands);
        var cancellations = new ListTag(); cancellationParents.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> cancellationTag(entry.getKey(), entry.getValue())).forEach(cancellations::add);
        tag.put("cancellationParents", cancellations); return tag;
    }
    static FrontierV3OperationLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != 5 || !tag.contains("operations", Tag.TAG_LIST))
            throw new IllegalArgumentException("unsupported physical operation journal format");
        var rows = tag.getList("operations", Tag.TAG_COMPOUND);
        if (!rows.equals(tag.get("operations")) || rows.size() > MAX_PENDING) throw new IllegalArgumentException("invalid operation journal rows");
        var values = new HashMap<UUID, FrontierV3OperationReceipt>();
        for (int i = 0; i < rows.size(); i++) {
            var value = FrontierV3OperationReceiptCodec.read(rows.getCompound(i));
            if (values.putIfAbsent(value.ticket(), value) != null) throw new IllegalArgumentException("duplicate operation ticket");
        }
        var ranges = new HashMap<Long, Long>();
        var rangeTags = tag.getList("retired", Tag.TAG_COMPOUND);
        if (!tag.contains("retired", Tag.TAG_LIST) || !rangeTags.equals(tag.get("retired")) || rangeTags.size() > MAX_PENDING + 1)
            throw new IllegalArgumentException("invalid physical retirement ranges");
        for (int i = 0; i < rangeTags.size(); i++) {
            var row = rangeTags.getCompound(i); long start = row.getLong("start"), end = row.getLong("end");
            if (!rangeTag(start, end).equals(row) || ranges.putIfAbsent(start, end) != null)
                throw new IllegalArgumentException("invalid or duplicate physical retirement range");
        }
        var commands = new HashMap<UUID, OwnerCommand>(); var commandTags = tag.getList("ownerCommands", Tag.TAG_COMPOUND);
        if (!tag.contains("ownerCommands", Tag.TAG_LIST) || !commandTags.equals(tag.get("ownerCommands")) || commandTags.size() > values.size())
            throw new IllegalArgumentException("invalid owner command list");
        for (int i = 0; i < commandTags.size(); i++) {
            var row = commandTags.getCompound(i); UUID id = row.getUUID("ticket");
            var command = new OwnerCommand(new io.farfrontier.palemirror.frontier.v3.api.CommandId(row.getString("command")),
                    io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs()
                            .decode(row.getString("type"), row.getByteArray("consequence")),
                    switch (row.getString("closure")) {
                        case "automatic" -> OwnerCommand.Closure.AUTOMATIC;
                        case "explicit-owner" -> OwnerCommand.Closure.EXPLICIT_OWNER;
                        default -> throw new IllegalArgumentException("unknown physical owner closure policy");
                    }, row.getByteArray("context"));
            if (!commandTag(id, command).equals(row) || commands.putIfAbsent(id, command) != null) throw new IllegalArgumentException("invalid owner command binding");
        }
        var cancellations = new HashMap<UUID, UUID>(); var cancelledTags = tag.getList("cancellationParents", Tag.TAG_COMPOUND);
        if (!tag.contains("cancellationParents", Tag.TAG_LIST) || !cancelledTags.equals(tag.get("cancellationParents"))
                || cancelledTags.size() > values.size()) throw new IllegalArgumentException("invalid conditional operation list");
        for (int i = 0; i < cancelledTags.size(); i++) {
            var row = cancelledTags.getCompound(i); var child = row.getUUID("child"); var parent = row.getUUID("parent");
            if (!cancellationTag(child, parent).equals(row) || cancellations.putIfAbsent(child, parent) != null)
                throw new IllegalArgumentException("invalid or duplicate conditional operation");
        }
        return new FrontierV3OperationLedger(values, ranges, commands, cancellations, tag.getLong("nextSequence"));
    }
    private static CompoundTag cancellationTag(UUID child, UUID parent) {
        var tag = new CompoundTag(); tag.putUUID("child", child); tag.putUUID("parent", parent); return tag;
    }
    private static CompoundTag rangeTag(long start, long end) {
        var tag = new CompoundTag(); tag.putLong("start", start); tag.putLong("end", end); return tag;
    }
    private static CompoundTag commandTag(UUID ticket, OwnerCommand command) {
        var tag = new CompoundTag(); tag.putUUID("ticket", ticket); tag.putString("command", command.id().value());
        tag.putString("fingerprint", command.fingerprint()); tag.putString("type", command.consequence().type());
        tag.putString("closure", switch (command.closure()) { case AUTOMATIC -> "automatic"; case EXPLICIT_OWNER -> "explicit-owner"; });
        tag.putByteArray("context", command.context());
        tag.putByteArray("consequence", io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs().encode(command.consequence())); return tag;
    }
}
