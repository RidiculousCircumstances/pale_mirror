package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Retains unresolved physical obligations, not business jobs or a second resource ledger.
 * Each producer remains responsible for validating its exact CURRENT canonical intent. */
final class FrontierV3OperationLedger extends FrontierV3JournaledSavedData {
    private static final int MAX_PENDING = 8192;
    static final int MAX_NATIVE_RECEIPTS = 16384;
    private int pendingParticipants;
    private record Key(FrontierV3OperationReceipt.Protocol protocol, io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, String operation) {
        static Key of(FrontierV3OperationReceipt.Declaration declaration) {
            return new Key(declaration.protocol(), declaration.owner(), declaration.operation());
        }
    }
    private final Map<UUID, FrontierV3OperationReceipt> operations;
    private final Map<Long, Long> retired;
    private final FrontierV3RetiredOperationRanges retiredIndex;
    record OwnerCommand(io.farfrontier.palemirror.frontier.v3.api.CommandId id, String fingerprint) {
        OwnerCommand { Objects.requireNonNull(id); if (fingerprint == null || fingerprint.isBlank()) throw new IllegalArgumentException("missing owner command fingerprint"); }
    }
    private final Map<UUID, OwnerCommand> ownerCommands;
    private long nextSequence;
    private final Map<Key, UUID> byOwner = new HashMap<>();
    private final Set<UUID> unattempted = new HashSet<>();
    private final Map<UUID, Long> observedSince = new HashMap<>(); // diagnostics only, reset explicitly on process recovery
    private FrontierV3OperationLedger(Map<UUID, FrontierV3OperationReceipt> initial,
            Map<Long, Long> ranges, Map<UUID, OwnerCommand> commands, long nextSequence) {
        super(FrontierV3PhysicalStoreKind.OPERATIONS);
        if (initial.size() > MAX_PENDING) throw new IllegalArgumentException("physical operation backlog exceeds its bound");
        operations = table("operations", initial, UUID::toString, (key, value) -> FrontierV3OperationReceiptCodec.write(value));
        retired = table("retired", ranges, Object::toString, FrontierV3OperationLedger::rangeTag);
        retiredIndex = new FrontierV3RetiredOperationRanges(retired,
                initial.values().stream().map(FrontierV3OperationReceipt::sequence).toList(), nextSequence);
        if (!initial.keySet().containsAll(commands.keySet()) || commands.values().stream().map(OwnerCommand::id).distinct().count() != commands.size())
            throw new IllegalArgumentException("owner command has no unique operation");
        ownerCommands = table("ownerCommands", commands, UUID::toString, FrontierV3OperationLedger::commandTag);
        if (nextSequence < 1 || initial.values().stream().map(FrontierV3OperationReceipt::sequence).distinct().count() != initial.size())
            throw new IllegalArgumentException("invalid physical operation causal order");
        this.nextSequence = nextSequence;
        initial.forEach((ticket, value) -> {
            observedSince.put(ticket, System.nanoTime());
            if (!ticket.equals(value.ticket()) || byOwner.putIfAbsent(Key.of(value.declaration()), ticket) != null)
                throw new IllegalArgumentException("competing recovered physical operation");
            if (value.sequence() >= nextSequence) throw new IllegalArgumentException("physical operation beyond issued sequence");
            pendingParticipants = Math.addExact(pendingParticipants, value.declaration().participants().size());
        });
        if (pendingParticipants > MAX_NATIVE_RECEIPTS) throw new IllegalArgumentException("physical participant backlog exceeds admission bound");
    }
    static FrontierV3OperationLedger inMemory() { return new FrontierV3OperationLedger(Map.of(), Map.of(), Map.of(), 1); }
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
        pendingParticipants += declaration.participants().size();
        observedSince.put(value.ticket(), System.nanoTime());
        unattempted.add(value.ticket()); setDirty(); return value;
    }
    boolean canPrepare(FrontierV3OperationReceipt.Declaration declaration) {
        return byOwner.containsKey(Key.of(declaration)) || operations.size() < MAX_PENDING
                && pendingParticipants <= MAX_NATIVE_RECEIPTS - declaration.participants().size();
    }
    Optional<FrontierV3OperationReceipt> find(FrontierV3OperationReceipt.Declaration declaration) {
        var ticket = byOwner.get(Key.of(declaration));
        return ticket == null ? Optional.empty() : Optional.of(require(ticket));
    }
    /** Must follow persist(). Even matching preimages do not permit replay after restart. */
    boolean beginAttempt(UUID ticket) {
        require(ticket);
        if (isDirty()) throw new IllegalStateException("physical operation has no durable prepare receipt");
        return unattempted.remove(ticket);
    }
    FrontierV3OperationReceipt require(UUID ticket) {
        var value = operations.get(ticket);
        if (value == null) throw new IllegalArgumentException("unknown or retired physical operation ticket");
        return value;
    }
    Optional<FrontierV3OperationReceipt> find(UUID ticket) { return Optional.ofNullable(operations.get(ticket)); }
    Map<UUID, OwnerCommand> ownerCommands() { return Map.copyOf(ownerCommands); }
    Optional<OwnerCommand> ownerCommand(UUID ticket) { require(ticket); return Optional.ofNullable(ownerCommands.get(ticket)); }
    /** Only the confirmation coordinator may supersede an unaccepted, pinned command.
     * The physical effect and its ticket remain unchanged. */
    void replaceUnacceptedOwnerCommand(UUID ticket, OwnerCommand expected, OwnerCommand successor) {
        var value = require(ticket);
        if (value.ownerClosed() || !value.applied().equals(value.declaration().participants())
                || !Objects.equals(ownerCommands.get(ticket), expected) || expected.id().equals(successor.id()))
            throw new IllegalArgumentException("stale or incomplete operation recovery confirmation");
        ownerCommands.put(ticket, successor); setDirty();
    }
    void bindOwnerCommand(UUID ticket, OwnerCommand command) {
        var value = require(ticket);
        if (!value.applied().equals(value.declaration().participants())) throw new IllegalStateException("owner command precedes applied participants");
        var prior = ownerCommands.get(ticket);
        if (prior != null && !prior.equals(command)) throw new IllegalArgumentException("owner confirmation changed payload/command identity");
        if (prior == null) { ownerCommands.put(ticket, command); setDirty(); }
    }
    void applied(FrontierV3OperationReceipt.NativeReceipt receipt) { replace(require(receipt.ticket()).applied(receipt)); }
    void saved(FrontierV3OperationReceipt.NativeReceipt receipt) { replace(require(receipt.ticket()).saved(receipt)); }
    void closeOwner(UUID ticket) { replace(require(ticket).closeOwner()); }
    private void replace(FrontierV3OperationReceipt value) {
        if (!value.equals(require(value.ticket()))) { operations.put(value.ticket(), value); setDirty(); }
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
        if (!unattempted.contains(ticket) || !value.applied().isEmpty()) return false;
        remove(value); return true;
    }
    private void remove(FrontierV3OperationReceipt value) {
        UUID ticket = value.ticket();
        observedSince.remove(ticket);
        pendingParticipants -= value.declaration().participants().size();
        operations.remove(ticket); byOwner.remove(Key.of(value.declaration()), ticket); ownerCommands.remove(ticket); unattempted.remove(ticket); setDirty();
        retiredIndex.add(value.sequence());
    }
    boolean prunable(FrontierV3OperationReceipt.NativeReceipt receipt) {
        return !operations.containsKey(receipt.ticket()) && retiredIndex.contains(receipt.sequence());
    }
    int pendingCount() { return operations.size(); }
    @Override protected String ownerDiagnostic() {
        long now = System.nanoTime();
        long age = observedSince.values().stream().mapToLong(since -> Math.max(0, now - since)).max().orElse(0) / 1_000_000;
        long nativeWaiting = operations.values().stream().filter(value -> !value.saved().equals(value.declaration().participants())).count();
        long ownerWaiting = operations.values().stream().filter(value -> !value.ownerClosed()).count();
        return "{\"pendingOperations\":" + operations.size() + ",\"pendingParticipants\":" + pendingParticipants
                + ",\"awaitingNativeSave\":" + nativeWaiting + ",\"awaitingOwner\":" + ownerWaiting
                + ",\"oldestObservedAgeMs\":" + age + ",\"ageScope\":\"current-process\",\"retiredRanges\":" + retired.size() + "}";
    }
    @Override protected CompoundTag metadata() { var tag = new CompoundTag(); tag.putInt("format", 2); tag.putLong("nextSequence", nextSequence); return tag; }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.merge(metadata()); var rows = new ListTag();
        operations.values().stream().sorted(Comparator.comparing(value -> value.ticket().toString()))
                .map(FrontierV3OperationReceiptCodec::write).forEach(rows::add); tag.put("operations", rows);
        var ranges = new ListTag(); retired.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> rangeTag(entry.getKey(), entry.getValue())).forEach(ranges::add);
        tag.put("retired", ranges);
        var commands = new ListTag(); ownerCommands.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> commandTag(entry.getKey(), entry.getValue())).forEach(commands::add);
        tag.put("ownerCommands", commands); return tag;
    }
    static FrontierV3OperationLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != 2 || !tag.contains("operations", Tag.TAG_LIST))
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
        if (!tag.contains("ownerCommands", Tag.TAG_LIST) || !commandTags.equals(tag.get("ownerCommands"))) throw new IllegalArgumentException("invalid owner command list");
        for (int i = 0; i < commandTags.size(); i++) {
            var row = commandTags.getCompound(i); UUID id = row.getUUID("ticket");
            var command = new OwnerCommand(new io.farfrontier.palemirror.frontier.v3.api.CommandId(row.getString("command")), row.getString("fingerprint"));
            if (!commandTag(id, command).equals(row) || commands.putIfAbsent(id, command) != null) throw new IllegalArgumentException("invalid owner command binding");
        }
        return new FrontierV3OperationLedger(values, ranges, commands, tag.getLong("nextSequence"));
    }
    private static CompoundTag rangeTag(long start, long end) {
        var tag = new CompoundTag(); tag.putLong("start", start); tag.putLong("end", end); return tag;
    }
    private static CompoundTag commandTag(UUID ticket, OwnerCommand command) {
        var tag = new CompoundTag(); tag.putUUID("ticket", ticket); tag.putString("command", command.id().value());
        tag.putString("fingerprint", command.fingerprint()); return tag;
    }
}
