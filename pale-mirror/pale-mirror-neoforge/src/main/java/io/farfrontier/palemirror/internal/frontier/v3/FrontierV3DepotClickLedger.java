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
    private static final int FORMAT = 3;
    private static final int MAX_PENDING = 1_024;
    private final Map<SubjectId, FrontierV3ContainerClickTransaction> pending;

    private FrontierV3DepotClickLedger() { this(new LinkedHashMap<>()); }
    private FrontierV3DepotClickLedger(Map<SubjectId, FrontierV3ContainerClickTransaction> pending) {
        super(FrontierV3PhysicalStoreKind.PLAYER_CLICKS);
        if (pending.size() > MAX_PENDING || pending.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(entry.getValue().id()))
                || pending.values().stream().flatMap(t -> t.members().stream()).map(FrontierV3DepotClickWitness::containerId)
                        .distinct().count() != pending.values().stream().mapToLong(t -> t.members().size()).sum()) {
            throw new IllegalArgumentException("invalid bounded depot click witnesses");
        }
        this.pending = table("pending", pending, SubjectId::value, (id, witness) -> witness.save());
    }

    static FrontierV3DepotClickLedger get(ServerLevel level) {
        return FrontierV3JournaledSavedData.get(level, FrontierV3PhysicalStoreKind.PLAYER_CLICKS, FrontierV3DepotClickLedger::new, FrontierV3DepotClickLedger::load);
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

    void observe(FrontierV3DepotClickWitness before, FrontierV3DepotClickWitness after) {
        observe(new FrontierV3ContainerClickTransaction(java.util.List.of(before)), new FrontierV3ContainerClickTransaction(java.util.List.of(after)));
    }
    void observe(FrontierV3ContainerClickTransaction before, FrontierV3ContainerClickTransaction after) {
        if (!before.id().equals(after.id()) || before.members().size() != after.members().size()
                || !before.equals(pending.get(before.id()))) {
            throw new IllegalArgumentException("depot click postcondition lacks its exact pre-effect witness");
        }
        for (int i = 0; i < before.members().size(); i++) {
            var a = before.members().get(i); var b = after.members().get(i);
            if (a.after().isPresent() || !a.containerId().equals(b.containerId()) || !a.accountId().equals(b.accountId())
                    || !a.ownerId().equals(b.ownerId()) || a.authorityEpoch() != b.authorityEpoch()
                    || !a.playerId().equals(b.playerId()) || !a.interactionId().equals(b.interactionId())
                    || !a.before().equals(b.before()) || !a.returnSource().equals(b.returnSource()) || b.after().isEmpty())
                throw new IllegalArgumentException("composed postimage changed its admitted predecessor");
        }
        pending.put(before.id(), after); setDirty();
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

    @Override protected CompoundTag metadata() { var tag = new CompoundTag(); tag.putInt("format", FORMAT); return tag; }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT);
        ListTag entries = new ListTag();
        pending.values().stream().sorted(Comparator.comparing(FrontierV3ContainerClickTransaction::id))
                .forEach(value -> entries.add(value.save()));
        tag.put("pending", entries);
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
        return new FrontierV3DepotClickLedger(values);
    }
}
