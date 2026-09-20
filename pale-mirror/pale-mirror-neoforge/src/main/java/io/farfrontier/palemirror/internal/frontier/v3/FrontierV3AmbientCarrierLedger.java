package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded inactive-body evidence; canonical actors and rosters remain domain-owned. */
final class FrontierV3AmbientCarrierLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_ambient_carriers";
    private static final int FORMAT = 3, MAX_CARRIERS = 4_096;
    private final Map<SubjectId, Carrier> carriers;
    private FrontierV3AmbientCarrierLedger() { this(new LinkedHashMap<>()); }
    private FrontierV3AmbientCarrierLedger(Map<SubjectId, Carrier> carriers) { this.carriers = carriers; }
    static FrontierV3AmbientCarrierLedger emptyForTest() { return new FrontierV3AmbientCarrierLedger(); }
    static FrontierV3AmbientCarrierLedger get(ServerLevel level, WorldId worldId) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3AmbientCarrierLedger::new, FrontierV3AmbientCarrierLedger::load,
                DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(worldId.value().getBytes(StandardCharsets.UTF_8)));
    }
    Reconciliation reconciliation(FrontierV3ActorCarrierComposition.Declaration live) {
        Carrier carrier = carriers.get(live.actorId());
        if (carrier == null) return Reconciliation.NO_FENCED_CARRIER;
        if (!carrier.identity.entityId().equals(live.entityId())) return Reconciliation.UUID_MISMATCH;
        if (carrier.identity.kind() != live.kind()) return Reconciliation.KIND_MISMATCH;
        if (live.representation() != FrontierV3ActorCarrierComposition.Representation.LIVE_BODY) return Reconciliation.REPRESENTATION_MISMATCH;
        long predecessor = live.owner() == FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE ? carrier.physicalRevision : carrier.ambientRevision;
        return live.authorityRevision() <= predecessor ? Reconciliation.STALE_REVISION : Reconciliation.READY;
    }
    /** Reject a live exact body plus a retained inactive carrier before either custody changes. */
    Reconciliation reconciliation(FrontierV3ActorCarrierComposition.Declaration live, boolean liveBodyPresent) {
        Reconciliation result = reconciliation(live);
        return liveBodyPresent && result != Reconciliation.NO_FENCED_CARRIER
                ? Reconciliation.CONCURRENT_CUSTODY : result;
    }
    boolean canFence(FrontierV3ActorCarrierComposition.Declaration inactive, long physicalRevision, long ambientRevision) {
        if (inactive.representation() != FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER || physicalRevision < 1L || ambientRevision < 0L
                || carriers.containsKey(inactive.actorId()) || carriers.size() >= MAX_CARRIERS) return false;
        return carriers.values().stream().noneMatch(value -> value.identity.entityId().equals(inactive.entityId()));
    }
    boolean fence(FrontierV3ActorCarrierComposition.Declaration inactive, long physicalRevision, long ambientRevision) {
        Carrier next = new Carrier(inactive, physicalRevision, ambientRevision);
        Carrier previous = carriers.get(inactive.actorId());
        if (previous != null) return previous.equals(next);
        if (!canFence(inactive, physicalRevision, ambientRevision)) return false;
        carriers.put(inactive.actorId(), next); setDirty(); return true;
    }
    boolean matchesCarrier(FrontierV3ActorCarrierComposition.Declaration inactive, long physicalRevision, long ambientRevision) {
        return new Carrier(inactive, physicalRevision, ambientRevision).equals(carriers.get(inactive.actorId()));
    }
    boolean hasCarrier(SubjectId actorId) { return carriers.containsKey(actorId); }
    long reconstructionEpoch(SubjectId actorId) { Carrier carrier = carriers.get(actorId); if (carrier == null) throw new IllegalStateException("missing v3 ambient carrier"); return Math.addExact(carrier.identity.epoch(), 1L); }
    boolean adopt(FrontierV3ActorCarrierComposition.Declaration live) { if (reconciliation(live) != Reconciliation.READY) return false; carriers.remove(live.actorId()); setDirty(); return true; }
    int inactiveCount() { return carriers.size(); }
    List<FrontierDomainRelationships.CarrierEvidence> relationshipEvidence() {
        return carriers.values().stream().sorted(Comparator.comparing(value -> value.identity.actorId().value())).map(value ->
                new FrontierDomainRelationships.CarrierEvidence(value.identity.actorId(), "carrier:" + value.identity.entityId(), "carrier-revision:" + value.physicalRevision)).toList();
    }
    static FrontierV3AmbientCarrierLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("incompatible v3 ambient carrier ledger");
        ListTag values = tag.getList("carriers", Tag.TAG_COMPOUND); if (values.size() > MAX_CARRIERS) throw new IllegalStateException("v3 ambient carrier limit exceeded");
        Map<SubjectId, Carrier> restored = new LinkedHashMap<>();
        for (Tag raw : values) {
            Carrier carrier = Carrier.load((CompoundTag) raw);
            if (restored.putIfAbsent(carrier.identity.actorId(), carrier) != null
                    || restored.values().stream().anyMatch(value -> !value.identity.actorId().equals(carrier.identity.actorId())
                    && value.identity.entityId().equals(carrier.identity.entityId()))) {
                throw new IllegalStateException("duplicate v3 ambient carrier evidence");
            }
        }
        return new FrontierV3AmbientCarrierLedger(restored);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag values = new ListTag();
        carriers.values().stream().sorted(Comparator.comparing(value -> value.identity.actorId().value())).forEach(value -> values.add(value.save()));
        tag.put("carriers", values); return tag;
    }
    enum Reconciliation {
        NO_FENCED_CARRIER, READY, UUID_MISMATCH, KIND_MISMATCH, REPRESENTATION_MISMATCH,
        STALE_REVISION, CONCURRENT_CUSTODY
    }
    record Carrier(FrontierV3ActorCarrierComposition.Declaration identity, long physicalRevision, long ambientRevision) {
        Carrier { if (identity.representation() != FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER || physicalRevision < 1L || ambientRevision < 0L) throw new IllegalArgumentException("invalid ambient carrier evidence"); }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag(); tag.putString("actor", identity.actorId().value()); tag.putUUID("uuid", identity.entityId());
            tag.putString("kind", identity.kind().name()); tag.putString("owner", identity.owner().name());
            tag.putString("representation", identity.representation().name()); tag.putLong("revision", physicalRevision);
            tag.putLong("ambientRevision", ambientRevision); tag.putLong("epoch", identity.epoch()); return tag;
        }
        static Carrier load(CompoundTag tag) {
            if (!tag.contains("actor", Tag.TAG_STRING) || !tag.hasUUID("uuid") || !tag.contains("kind", Tag.TAG_STRING)
                    || !tag.contains("owner", Tag.TAG_STRING) || !tag.contains("representation", Tag.TAG_STRING)
                    || !tag.contains("revision", Tag.TAG_LONG) || !tag.contains("ambientRevision", Tag.TAG_LONG)
                    || !tag.contains("epoch", Tag.TAG_LONG)) throw new IllegalStateException("incomplete v3 ambient carrier evidence");
            try { return new Carrier(new FrontierV3ActorCarrierComposition.Declaration(new SubjectId(tag.getString("actor")),
                    FrontierV3ActorCarrierComposition.ActorKind.valueOf(tag.getString("kind")), FrontierV3ActorCarrierComposition.Owner.valueOf(tag.getString("owner")),
                    tag.getUUID("uuid"), FrontierV3ActorCarrierComposition.Representation.valueOf(tag.getString("representation")),
                    tag.getLong("revision"), tag.getLong("epoch")), tag.getLong("revision"), tag.getLong("ambientRevision")); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid v3 ambient carrier declaration", invalid); }
        }
    }
}
