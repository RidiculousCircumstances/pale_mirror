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

import java.util.Comparator;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded physical reconstruction evidence for one safely released ambient body.
 *
 * <p>This is deliberately not a second actor ledger.  The canonical document owns the actor,
 * job, inventory and cursor; this SavedData holds only the identity proof required to create the
 * next Java body after a COLD interval.  A record exists only between the fenced HOT release and
 * successful re-adoption, so it cannot become concurrent body custody.</p>
 */
final class FrontierV3AmbientCarrierLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_ambient_carriers";
    private static final int FORMAT = 2, MAX_CARRIERS = 4_096;
    private final Map<SubjectId, Carrier> carriers;

    private FrontierV3AmbientCarrierLedger() { this(new LinkedHashMap<>()); }
    private FrontierV3AmbientCarrierLedger(Map<SubjectId, Carrier> carriers) { this.carriers = carriers; }

    static FrontierV3AmbientCarrierLedger emptyForTest() { return new FrontierV3AmbientCarrierLedger(); }

    /**
     * SavedData is scoped by Minecraft level, while canonical runtimes can be isolated within
     * that level (notably ordinary GameTest worlds).  The physical-custody fence must therefore
     * be scoped by canonical WorldId as well: an inactive actor in one canonical world can never
     * make an unrelated world's deterministic UUID look foreign or already fenced.
     */
    static FrontierV3AmbientCarrierLedger get(ServerLevel level, WorldId worldId) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3AmbientCarrierLedger::new,
                FrontierV3AmbientCarrierLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE),
                NAME + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(worldId.value().getBytes(StandardCharsets.UTF_8)));
    }

    /** Checks the no-replacement admission boundary without changing the carrier. */
    Reconciliation reconciliation(SubjectId actorId, UUID entityId, long nextLeaseRevision) {
        Carrier carrier = carriers.get(actorId);
        if (carrier == null) return Reconciliation.NO_FENCED_CARRIER;
        if (!carrier.entityId().equals(entityId)) return Reconciliation.UUID_MISMATCH;
        // A scene revision and an ambient-lease revision are distinct clocks.  A resource
        // scene may be the first physical owner of an actor, in which case its checkpoint can
        // be much newer than the first ambient lease (revision one).  The carrier retains both
        // facts: physicalRevision is auditable physical custody evidence, while
        // ambientRevision is the only comparable predecessor for ambient re-adoption.
        if (nextLeaseRevision <= carrier.ambientRevision()) return Reconciliation.STALE_REVISION;
        return Reconciliation.READY;
    }

    /**
     * Checks an exact scene's natural return against the physical clock that fenced the body.
     * A scene is allowed to reconstruct only the same UUID at a later physical checkpoint; it
     * never turns an inactive carrier into authority for a different worker or an older scene.
     */
    Reconciliation sceneReconciliation(SubjectId actorId, UUID entityId, long nextPhysicalRevision) {
        Carrier carrier = carriers.get(actorId);
        if (carrier == null) return Reconciliation.NO_FENCED_CARRIER;
        if (!carrier.entityId().equals(entityId)) return Reconciliation.UUID_MISMATCH;
        if (nextPhysicalRevision <= carrier.physicalRevision()) return Reconciliation.STALE_REVISION;
        return Reconciliation.READY;
    }

    /** Fences one already checkpointed HOT body before it is discarded. */
    boolean fence(SubjectId actorId, UUID entityId, long physicalRevision, long ambientRevision, long liveEpoch) {
        if (physicalRevision < 1L || ambientRevision < 0L || liveEpoch < 1L) return false;
        Carrier next = new Carrier(actorId, entityId, physicalRevision, ambientRevision, liveEpoch);
        Carrier previous = carriers.get(actorId);
        if (previous != null) return previous.equals(next);
        if (carriers.size() >= MAX_CARRIERS || carriers.values().stream().anyMatch(value -> value.entityId().equals(entityId))) return false;
        carriers.put(actorId, next); setDirty(); return true;
    }

    boolean canFence(SubjectId actorId, UUID entityId, long physicalRevision, long ambientRevision, long liveEpoch) {
        if (physicalRevision < 1L || ambientRevision < 0L || liveEpoch < 1L || carriers.containsKey(actorId) || carriers.size() >= MAX_CARRIERS) return false;
        return carriers.values().stream().noneMatch(value -> value.entityId().equals(entityId));
    }

    /** A retry may observe the exact proof written before an unaccepted scene close. */
    boolean matchesCarrier(SubjectId actorId, UUID entityId, long physicalRevision, long ambientRevision, long liveEpoch) {
        return new Carrier(actorId, entityId, physicalRevision, ambientRevision, liveEpoch).equals(carriers.get(actorId));
    }

    boolean hasCarrier(SubjectId actorId) { return carriers.containsKey(actorId); }

    long reconstructionEpoch(SubjectId actorId) {
        Carrier carrier = carriers.get(actorId);
        if (carrier == null) throw new IllegalStateException("missing v3 ambient carrier");
        return Math.addExact(carrier.liveEpoch(), 1L);
    }

    /** Consumes exactly one inactive carrier after successful/new physical adoption. */
    boolean adopt(SubjectId actorId, UUID entityId, long nextLeaseRevision) {
        if (reconciliation(actorId, entityId, nextLeaseRevision) != Reconciliation.READY) return false;
        carriers.remove(actorId); setDirty(); return true;
    }

    /** Consumes a fenced carrier only after its later exact scene body was admitted. */
    boolean adoptScene(SubjectId actorId, UUID entityId, long nextPhysicalRevision) {
        if (sceneReconciliation(actorId, entityId, nextPhysicalRevision) != Reconciliation.READY) return false;
        carriers.remove(actorId); setDirty(); return true;
    }

    int inactiveCount() { return carriers.size(); }

    /** Bounded adapter evidence only; canonical actor and relationship ownership remain in Frontier state. */
    List<FrontierDomainRelationships.CarrierEvidence> relationshipEvidence() {
        return carriers.values().stream().sorted(Comparator.comparing(value -> value.actorId().value()))
                .map(value -> new FrontierDomainRelationships.CarrierEvidence(value.actorId(), "carrier:" + value.entityId(),
                        "carrier-revision:" + value.physicalRevision())).toList();
    }

    static FrontierV3AmbientCarrierLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("incompatible v3 ambient carrier ledger");
        ListTag values = tag.getList("carriers", Tag.TAG_COMPOUND);
        if (values.size() > MAX_CARRIERS) throw new IllegalStateException("v3 ambient carrier limit exceeded");
        Map<SubjectId, Carrier> restored = new LinkedHashMap<>();
        for (Tag raw : values) {
            Carrier carrier = Carrier.load((CompoundTag) raw);
            if (restored.putIfAbsent(carrier.actorId(), carrier) != null
                    || restored.values().stream().filter(value -> !value.actorId().equals(carrier.actorId()))
                    .anyMatch(value -> value.entityId().equals(carrier.entityId()))) {
                throw new IllegalStateException("duplicate v3 ambient carrier evidence");
            }
        }
        return new FrontierV3AmbientCarrierLedger(restored);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag values = new ListTag();
        carriers.values().stream().sorted(Comparator.comparing(value -> value.actorId().value())).forEach(value -> values.add(value.save()));
        tag.put("carriers", values); return tag;
    }

    enum Reconciliation { NO_FENCED_CARRIER, READY, UUID_MISMATCH, STALE_REVISION }

    record Carrier(SubjectId actorId, UUID entityId, long physicalRevision, long ambientRevision, long liveEpoch) {
        Carrier {
            Objects.requireNonNull(actorId, "actor id"); Objects.requireNonNull(entityId, "entity id");
            if (physicalRevision < 1L || ambientRevision < 0L || liveEpoch < 1L) throw new IllegalArgumentException("invalid ambient carrier evidence");
        }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag(); tag.putString("actor", actorId.value()); tag.putUUID("uuid", entityId);
            tag.putLong("revision", physicalRevision); tag.putLong("ambientRevision", ambientRevision); tag.putLong("epoch", liveEpoch); return tag;
        }
        static Carrier load(CompoundTag tag) {
            if (!tag.contains("actor", Tag.TAG_STRING) || !tag.hasUUID("uuid") || !tag.contains("revision", Tag.TAG_LONG)
                    || !tag.contains("ambientRevision", Tag.TAG_LONG) || !tag.contains("epoch", Tag.TAG_LONG)) throw new IllegalStateException("incomplete v3 ambient carrier evidence");
            return new Carrier(new SubjectId(tag.getString("actor")), tag.getUUID("uuid"), tag.getLong("revision"), tag.getLong("ambientRevision"), tag.getLong("epoch"));
        }
    }
}
