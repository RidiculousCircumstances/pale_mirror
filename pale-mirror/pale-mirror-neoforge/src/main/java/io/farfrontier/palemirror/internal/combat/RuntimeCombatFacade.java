package io.farfrontier.palemirror.internal.combat;

import java.util.UUID;
import java.util.function.Consumer;

import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.adapter.ActorDamageResult;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.adapter.SourceThreatAdapter;
import io.farfrontier.palemirror.internal.adapter.VanillaAnchorAdapter;
import io.farfrontier.palemirror.internal.observation.EncounterActorDestroyed;
import io.farfrontier.palemirror.internal.observation.GatePartDestroyed;
import io.farfrontier.palemirror.internal.observation.Observation;
import io.farfrontier.palemirror.internal.observation.ThreatControllerDestroyed;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.SourceGatePartRef;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/** Owns physical threat/gate combat provenance checks outside the server coordinator. */
public final class RuntimeCombatFacade {
    private final PaleMirrorSavedData data;
    private final Consumer<Observation> observations;

    public RuntimeCombatFacade(PaleMirrorSavedData data, Consumer<Observation> observations) {
        this.data = data;
        this.observations = observations;
    }

    public void threatDestroyed(String objectId, String causationId) {
        WorldObjectId id = new WorldObjectId(objectId);
        if (data.testMines().containsKey(id)) observations.accept(new ThreatControllerDestroyed(
                "controller-destroyed:" + causationId, id, causationId));
    }

    public boolean controllerVulnerable(String objectId) {
        try {
            return data.worldState().facility(new WorldObjectId(objectId))
                    .map(FacilityState::controllerVulnerable).orElse(false);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public ActorDamageResult receiveThreatControllerDamage(Entity entity, DamageSource source, float amount) {
        return ThreatControllerCombatRuntime.receive(data, entity, source, amount, observations);
    }

    public void gateEntityDestroyed(String objectId, String slotId, UUID entityId) {
        try {
            WorldObjectId id = new WorldObjectId(objectId);
            TestMineRecord mine = data.testMines().get(id);
            FacilityState facility = data.worldState().facility(id).orElse(null);
            if (mine == null || facility == null || mine.gate().part(slotId)
                    .filter(part -> entityId.equals(part.entityId())).isEmpty()) return;
            observations.accept(new GatePartDestroyed("gate-part:" + id.value() + ":" + slotId + ":" + entityId,
                    id, slotId, "entity:" + entityId));
        } catch (IllegalArgumentException ignored) {
            // Entity data is untrusted until it matches a registered PM reference.
        }
    }

    public void gateBlockDestroyed(ServerLevel level, BlockPos position) {
        for (TestMineRecord mine : data.testMines().values()) {
            if (!mine.dimensionId().equals(level.dimension().location().toString())) continue;
            FacilityState facility = data.worldState().facility(mine.id()).orElse(null);
            if (facility == null) continue;
            AdapterRegistry.sourceAdapter(facility.infectionSource()).gatePartAt(level, mine, position).ifPresent(slot ->
                    observations.accept(new GatePartDestroyed("gate-block:" + mine.id().value() + ":" + slot + ":"
                            + facility.desiredRevision(), mine.id(), slot, "block:" + position.asLong())));
        }
    }

    public ActorDamageResult receiveSourceActorDamage(Entity entity, DamageSource source, float amount) {
        SourceThreatAdapter gateClaimant = AdapterRegistry.sourceAdapters().stream()
                .filter(adapter -> adapter.matchesGatePart(entity)).findFirst().orElse(null);
        if (gateClaimant != null) return receiveSourceGateDamage(gateClaimant, entity, source, amount);
        SourceThreatAdapter claimant = AdapterRegistry.sourceAdapters().stream()
                .filter(adapter -> adapter.matchesActor(entity)).findFirst().orElse(null);
        if (claimant == null) return ActorDamageResult.passThrough();
        if (!(entity.level() instanceof ServerLevel level)) return ActorDamageResult.blocked("PM actor is not in a server level");
        String objectId = entity.getPersistentData().getString(VanillaAnchorAdapter.OBJECT_ID_KEY);
        String slotId = entity.getPersistentData().getString("pale_mirror_encounter_slot");
        if (objectId.isBlank() || slotId.isBlank()) return ActorDamageResult.blocked("PM actor lacks persisted provenance");
        try {
            WorldObjectId facilityId = new WorldObjectId(objectId);
            TestMineRecord mine = data.testMines().get(facilityId);
            FacilityState facility = data.worldState().facility(facilityId).orElse(null);
            if (mine == null || facility == null || !mine.dimensionId().equals(level.dimension().location().toString())) {
                return ActorDamageResult.blocked("PM actor is not attached to its registered threat site");
            }
            var reference = mine.encounter().actor(slotId).orElse(null);
            if (!claimant.source().equals(facility.infectionSource()) || reference == null
                    || !claimant.matchesOwnedActor(entity, mine, slotId)) {
                return ActorDamageResult.blocked("PM actor provenance does not match its canonical source and slot");
            }
            ActorDamageResult result = claimant.receiveDamage(level, mine, entity, reference, source, amount);
            if (!result.intercepts()) return result;
            data.setDirty();
            if (result.disposition() == ActorDamageResult.Disposition.DEFEATED) {
                String causationId = "combat:" + entity.getUUID();
                observations.accept(new EncounterActorDestroyed("encounter-actor-destroyed:" + claimant.source().value()
                        + ":" + causationId, facilityId, claimant.source(), slotId, entity.getUUID()));
            }
            return result;
        } catch (IllegalArgumentException ignored) {
            return ActorDamageResult.blocked("PM actor contains an invalid persisted world object id");
        }
    }

    private ActorDamageResult receiveSourceGateDamage(SourceThreatAdapter claimant, Entity entity,
                                                      DamageSource source, float amount) {
        if (!(entity.level() instanceof ServerLevel level)) return ActorDamageResult.blocked("PM gate actor is not in a server level");
        String objectId = entity.getPersistentData().getString(VanillaAnchorAdapter.OBJECT_ID_KEY);
        String slotId = entity.getPersistentData().getString("pale_mirror_encounter_slot");
        try {
            WorldObjectId facilityId = new WorldObjectId(objectId);
            TestMineRecord mine = data.testMines().get(facilityId);
            if (mine == null || !mine.dimensionId().equals(level.dimension().location().toString())) {
                return ActorDamageResult.blocked("PM gate actor is not attached to its registered site");
            }
            FacilityState facility = data.worldState().facility(facilityId).orElse(null);
            SourceGatePartRef part = mine.gate().part(slotId).orElse(null);
            if (facility == null || !claimant.source().equals(facility.infectionSource()) || part == null
                    || !entity.getUUID().equals(part.entityId()) || !claimant.matchesOwnedGatePart(entity, mine, slotId)) {
                return ActorDamageResult.blocked("PM gate actor identity is stale");
            }
            ActorDamageResult result = claimant.receiveGateDamage(level, mine, entity, part, source, amount);
            if (result.intercepts()) data.setDirty();
            if (result.disposition() == ActorDamageResult.Disposition.DEFEATED) {
                gateEntityDestroyed(objectId, slotId, entity.getUUID());
            }
            return result;
        } catch (IllegalArgumentException ignored) {
            return ActorDamageResult.blocked("PM gate actor contains invalid provenance");
        }
    }
}
