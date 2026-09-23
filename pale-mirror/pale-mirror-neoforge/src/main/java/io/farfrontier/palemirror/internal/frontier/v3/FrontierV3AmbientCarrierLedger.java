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
    private static final int FORMAT = 5, MAX_CARRIERS = 4_096;
    private final Map<SubjectId, Carrier> carriers;
    private final Map<SubjectId, FrontierV3SceneDeparture> departures = new LinkedHashMap<>();
    private final Map<SubjectId, FrontierV3SceneDeparture> departureConflicts = new LinkedHashMap<>();
    private FrontierV3AmbientCarrierLedger() { this(new LinkedHashMap<>()); }
    private FrontierV3AmbientCarrierLedger(Map<SubjectId, Carrier> carriers) { this.carriers = carriers; }
    static FrontierV3AmbientCarrierLedger emptyForTest() { return new FrontierV3AmbientCarrierLedger(); }
    static FrontierV3AmbientCarrierLedger get(ServerLevel level, WorldId worldId) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3AmbientCarrierLedger::new, FrontierV3AmbientCarrierLedger::load,
                DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(worldId.value().getBytes(StandardCharsets.UTF_8)));
    }
    Reconciliation reconciliation(FrontierV3ActorCarrierComposition.Declaration live) {
        if (hasDepartureConflict(live.actorId())) return Reconciliation.DEPARTURE_CONFLICT;
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
                || hasDepartureConflict(inactive.actorId()) || carriers.containsKey(inactive.actorId()) || carriers.size() >= MAX_CARRIERS) return false;
        return carriers.values().stream().noneMatch(value -> value.identity.entityId().equals(inactive.entityId()));
    }
    boolean fence(FrontierV3ActorCarrierComposition.Declaration inactive, long physicalRevision, long ambientRevision) {
        if (hasDepartureConflict(inactive.actorId())) return false;
        Carrier next = new Carrier(inactive, physicalRevision, ambientRevision);
        Carrier previous = carriers.get(inactive.actorId());
        if (previous != null) return previous.equals(next);
        if (!canFence(inactive, physicalRevision, ambientRevision)) return false;
        carriers.put(inactive.actorId(), next); setDirty(); return true;
    }
    /**
     * Records the one lawful no-body recovery edge for a closed scene.  The caller has already
     * proved the exact scene member's naturally loaded entity storage contains no body; this
     * method deliberately refuses to turn an arbitrary missing actor into a carrier.
     */
    boolean fenceObservedAbsentClosedScene(FrontierV3ActorCarrierComposition.Declaration inactive,
                                           long physicalRevision, long ambientRevision, boolean absenceObserved) {
        return absenceObserved
                && inactive.owner() == FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE
                && inactive.representation() == FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER
                && fence(inactive, physicalRevision, ambientRevision);
    }
    boolean matchesCarrier(FrontierV3ActorCarrierComposition.Declaration inactive, long physicalRevision, long ambientRevision) {
        return new Carrier(inactive, physicalRevision, ambientRevision).equals(carriers.get(inactive.actorId()));
    }
    boolean hasCarrier(SubjectId actorId) { return carriers.containsKey(actorId); }
    /** A receipt is evidence only; recording it does not install an inactive carrier. */
    boolean recordDeparture(FrontierV3SceneDeparture departure) {
        SubjectId actor = departure.carrier().identity().actorId();
        var previous = departures.get(actor);
        if (previous != null) {
            if (!previous.equals(departure) && departureConflicts.putIfAbsent(actor, departure) == null) setDirty();
            return previous.equals(departure) && !hasDepartureConflict(actor);
        }
        if (departures.size() >= MAX_CARRIERS) return false;
        if (departures.values().stream().anyMatch(value -> value.carrier().identity().entityId().equals(departure.carrier().identity().entityId()))) return false;
        departures.put(actor, departure);
        setDirty();
        return true;
    }
    java.util.Optional<FrontierV3SceneDeparture> departure(SubjectId actor) {
        return java.util.Optional.ofNullable(departures.get(actor));
    }
    boolean hasDepartureConflict(SubjectId actor) { return departureConflicts.containsKey(actor); }
    boolean retireDeadActor(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState state, SubjectId actor) {
        var location = state.actorLocations().get(actor);
        if (location == null || location.condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD
                || state.fencedRecovery().current().containsKey(
                        io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLeaseStateSupport.bodyRecoveryBindingId(actor))) return false;
        if (carriers.remove(actor) != null) setDirty();
        forgetDeparture(actor);
        return true;
    }
    /** Explicit resolved cleanup; mere reappearance must not discard contradictory evidence. */
    void forgetDeparture(SubjectId actor) {
        boolean changed = departures.remove(actor) != null;
        changed |= departureConflicts.remove(actor) != null;
        if (changed) setDirty();
    }
    /** Withdraw only this exact provisional fence when the same scene body returns before release. */
    boolean resumeDeparture(FrontierV3SceneDeparture receipt) {
        SubjectId actor = receipt.carrier().identity().actorId();
        if (hasDepartureConflict(actor) || !receipt.equals(departures.get(actor))) return false;
        Carrier fenced = carriers.get(actor);
        if (fenced != null && !fenced.equals(receipt.carrier())) return false;
        carriers.remove(actor);
        forgetDeparture(actor);
        return true;
    }
    long reconstructionEpoch(SubjectId actorId) { Carrier carrier = carriers.get(actorId); if (carrier == null) throw new IllegalStateException("missing v3 ambient carrier"); return Math.addExact(carrier.identity.epoch(), 1L); }
    boolean adopt(FrontierV3ActorCarrierComposition.Declaration live) { if (reconciliation(live) != Reconciliation.READY) return false; carriers.remove(live.actorId()); forgetDeparture(live.actorId()); setDirty(); return true; }
    int inactiveCount() { return carriers.size(); }
    List<FrontierDomainRelationships.CarrierEvidence> relationshipEvidence() {
        return carriers.values().stream().sorted(Comparator.comparing(value -> value.identity.actorId().value())).map(value ->
                new FrontierDomainRelationships.CarrierEvidence(value.identity.actorId(), "carrier:" + value.identity.entityId(), "carrier-revision:" + value.physicalRevision)).toList();
    }
    static FrontierV3AmbientCarrierLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("incompatible v3 ambient carrier ledger");
        ListTag values = inventory(tag, "carriers");
        Map<SubjectId, Carrier> restored = new LinkedHashMap<>();
        for (Tag raw : values) {
            Carrier carrier = Carrier.load((CompoundTag) raw);
            if (restored.putIfAbsent(carrier.identity.actorId(), carrier) != null
                    || restored.values().stream().anyMatch(value -> !value.identity.actorId().equals(carrier.identity.actorId())
                    && value.identity.entityId().equals(carrier.identity.entityId()))) {
                throw new IllegalStateException("duplicate v3 ambient carrier evidence");
            }
        }
        ListTag departures = inventory(tag, "departures");
        var ledger = new FrontierV3AmbientCarrierLedger(restored);
        for (Tag raw : departures) {
            var departure = FrontierV3SceneDeparture.load((CompoundTag) raw);
            if (ledger.departures.containsKey(departure.carrier().identity().actorId()) || !ledger.recordDeparture(departure)) {
                throw new IllegalStateException("duplicate scene departure evidence");
            }
        }
        for (Tag raw : inventory(tag, "departureConflicts")) {
            var conflict = FrontierV3SceneDeparture.load((CompoundTag) raw);
            var actor = conflict.carrier().identity().actorId();
            var first = ledger.departures.get(actor);
            if (first == null || first.equals(conflict) || ledger.departureConflicts.putIfAbsent(actor, conflict) != null)
                throw new IllegalStateException("invalid scene departure conflict evidence");
        }
        ledger.setDirty(false);
        return ledger;
    }
    private static ListTag inventory(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag rows) || (!rows.isEmpty() && rows.getElementType() != Tag.TAG_COMPOUND)
                || rows.size() > MAX_CARRIERS) throw new IllegalStateException("invalid carrier inventory: " + key);
        return rows;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag values = new ListTag();
        carriers.values().stream().sorted(Comparator.comparing(value -> value.identity.actorId().value())).forEach(value -> values.add(value.save()));
        tag.put("carriers", values);
        ListTag departureTags = new ListTag();
        departures.values().stream().sorted(Comparator.comparing(value -> value.carrier().identity().actorId()))
                .forEach(value -> departureTags.add(value.save()));
        tag.put("departures", departureTags);
        ListTag conflicts = new ListTag();
        departureConflicts.values().stream().sorted(Comparator.comparing(value -> value.carrier().identity().actorId()))
                .forEach(value -> conflicts.add(value.save()));
        tag.put("departureConflicts", conflicts);
        return tag;
    }
    enum Reconciliation {
        NO_FENCED_CARRIER, READY, UUID_MISMATCH, KIND_MISMATCH, REPRESENTATION_MISMATCH,
        STALE_REVISION, CONCURRENT_CUSTODY, DEPARTURE_CONFLICT
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
