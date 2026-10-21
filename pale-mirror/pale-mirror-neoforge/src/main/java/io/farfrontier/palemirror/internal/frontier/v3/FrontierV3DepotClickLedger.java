package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Durable pre/post physical witness for one player edit at each PM-owned depot. */
final class FrontierV3DepotClickLedger extends FrontierV3JournaledSavedData {
    private static final int FORMAT = 4;
    private static final int MAX_PENDING = 1_024;
    private final Map<SubjectId, FrontierV3ContainerClickTransaction> pending;
    private final FrontierV3LocalOperationReceipts completed;

    private FrontierV3DepotClickLedger() { this(new LinkedHashMap<>(), Map.of()); }
    private FrontierV3DepotClickLedger(Map<SubjectId, FrontierV3ContainerClickTransaction> pending,
            Map<java.util.UUID, FrontierV3OperationReceipt> completed) {
        super(FrontierV3PhysicalStoreKind.PLAYER_CLICKS);
        if (pending.size() > MAX_PENDING || pending.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(entry.getValue().id()))
                || pending.values().stream().flatMap(t -> t.members().stream()).map(FrontierV3DepotClickWitness::containerId)
                        .distinct().count() != pending.values().stream().mapToLong(t -> t.members().size()).sum()) {
            throw new IllegalArgumentException("invalid bounded depot click witnesses");
        }
        this.pending = table("pending", pending, SubjectId::value, (id, witness) -> witness.save());
        if (pending.values().stream().flatMap(value -> value.operation().stream()).anyMatch(value -> completed.containsKey(value.ticket())))
            throw new IllegalArgumentException("completed click still retains a pending operation owner");
        this.completed = new FrontierV3LocalOperationReceipts(table("completed", completed, java.util.UUID::toString,
                (id, operation) -> FrontierV3OperationReceiptCodec.write(operation)));
    }

    static FrontierV3DepotClickLedger get(ServerLevel level) {
        var ledger = FrontierV3JournaledSavedData.get(level, FrontierV3PhysicalStoreKind.PLAYER_CLICKS, FrontierV3DepotClickLedger::new, FrontierV3DepotClickLedger::load);
        ledger.complete(level); return ledger;
    }

    FrontierV3ContainerClickTransaction transaction(SubjectId containerId) {
        return pending.values().stream().filter(t -> t.contains(containerId)).findFirst().orElse(null);
    }
    FrontierV3DepotClickWitness pending(SubjectId containerId) {
        var transaction = transaction(containerId);
        return transaction == null ? null : transaction.members().stream().filter(m -> m.containerId().equals(containerId)).findFirst().orElseThrow();
    }
    java.util.List<FrontierV3DepotClickWitness> pending() {
        return transactions().stream().flatMap(t -> t.members().stream()).toList();
    }
    java.util.List<FrontierV3ContainerClickTransaction> transactions() {
        return pending.values().stream().sorted(Comparator.comparing(FrontierV3ContainerClickTransaction::id)).toList();
    }

    void prepare(FrontierV3DepotClickWitness witness) {
        prepare(new FrontierV3ContainerClickTransaction(java.util.List.of(witness)));
    }
    void prepare(FrontierV3ContainerClickTransaction witness) {
        if (witness.members().stream().anyMatch(m -> transaction(m.containerId()) != null)
                || pending.size() >= MAX_PENDING) {
            throw new IllegalArgumentException("depot already has a pending player click");
        }
        pending.put(witness.id(), witness); setDirty();
    }


    void retire(FrontierV3DepotClickWitness witness) {
        retire(new FrontierV3ContainerClickTransaction(java.util.List.of(witness)));
    }
    void retire(FrontierV3ContainerClickTransaction witness) {
        if (!witness.equals(pending.get(witness.id()))) {
            throw new IllegalArgumentException("depot click retirement lacks its current witness");
        }
        pending.remove(witness.id()); setDirty();
    }
    FrontierV3ContainerClickTransaction bind(FrontierV3ContainerClickTransaction witness, FrontierV3OperationReceipt receipt) {
        if (!witness.equals(pending.get(witness.id()))) throw new IllegalArgumentException("click lost its operation witness");
        if (witness.operation().isPresent()) {
            var prior = witness.operation().orElseThrow();
            if (!prior.ticket().equals(receipt.ticket()) || prior.sequence() != receipt.sequence()
                    || !prior.declaration().equals(receipt.declaration())) throw new IllegalArgumentException("click changed native operation");
            return witness;
        }
        var next = new FrontierV3ContainerClickTransaction(witness.members(), java.util.Optional.of(receipt), witness.outcome());
        pending.put(witness.id(), next); setDirty(); return next;
    }
    void capture(FrontierV3ContainerClickTransaction witness, io.farfrontier.palemirror.frontier.v3.model.ContainerPlayerEditObserved outcome) {
        if (!witness.equals(pending.get(witness.id())) || witness.outcome().isPresent())
            throw new IllegalArgumentException("native click outcome has no exact unfinished witness");
        pending.put(witness.id(), new FrontierV3ContainerClickTransaction(witness.members(), witness.operation(), java.util.Optional.of(outcome)));
        setDirty();
    }
    void retireNoEffect(FrontierV3ContainerClickTransaction witness, FrontierV3OperationReceipt receipt, ServerLevel level) {
        completed.add(receipt); retire(witness); complete(level);
    }
    private void complete(ServerLevel level) { completed.complete(level, () -> persist(level)); }

    @Override protected CompoundTag metadata() { var tag = new CompoundTag(); tag.putInt("format", FORMAT); return tag; }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT);
        ListTag entries = new ListTag();
        pending.values().stream().sorted(Comparator.comparing(FrontierV3ContainerClickTransaction::id))
                .forEach(value -> entries.add(value.save()));
        tag.put("pending", entries);
        tag.put("completed", completed.write());
        return tag;
    }

    static FrontierV3DepotClickLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalArgumentException("incompatible depot click witness format");
        Map<SubjectId, FrontierV3ContainerClickTransaction> values = new LinkedHashMap<>();
        ListTag entries = tag.getList("pending", Tag.TAG_COMPOUND);
        if (entries.size() > MAX_PENDING) throw new IllegalArgumentException("depot click witness limit exceeded");
        for (int index = 0; index < entries.size(); index++) {
            var witness = FrontierV3ContainerClickTransaction.read(entries.getCompound(index));
            if (values.put(witness.id(), witness) != null) throw new IllegalArgumentException("duplicate depot click witness");
        }
        return new FrontierV3DepotClickLedger(values, FrontierV3LocalOperationReceipts.read(tag, "completed"));
    }
}
