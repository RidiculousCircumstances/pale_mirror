package io.farfrontier.palemirror.internal.combat;

import io.farfrontier.palemirror.api.PaleMirrorVisuals;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.adapter.ActorDamageResult;
import io.farfrontier.palemirror.internal.observation.Observation;
import io.farfrontier.palemirror.internal.observation.ThreatControllerDestroyed;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/** Product Threat Heart combat authority; the carrier's vanilla health is never progression. */
public final class ThreatControllerCombatRuntime {
    private ThreatControllerCombatRuntime() { }

    public static ActorDamageResult receive(PaleMirrorSavedData data, Entity entity, DamageSource source, float amount,
                                            Consumer<Observation> observations) {
        var provider = PaleMirrorVisuals.provider().orElse(null);
        String objectId = provider == null ? null : provider.threatControllerObjectId(entity).orElse(null);
        if (objectId == null) return ActorDamageResult.passThrough();
        if (!(entity.level() instanceof ServerLevel level)) return ActorDamageResult.blocked("Threat controller is not server-side");
        try {
            WorldObjectId facilityId = new WorldObjectId(objectId);
            var mine = data.testMines().get(facilityId);
            if (mine == null || !mine.dimensionId().equals(level.dimension().location().toString())
                    || !entity.getUUID().equals(mine.anchorId())) {
                return ActorDamageResult.blocked("Threat controller identity is not the persisted facility reference");
            }
            if (!data.worldState().facility(facilityId).map(FacilityState::controllerVulnerable).orElse(false)) {
                return ActorDamageResult.blocked("Threat controller gates are still active");
            }
            String key = ThreatCombatLedger.actorKey("pale_mirror", objectId, "controller", "heart");
            ThreatActorControlState control = data.threatCombat().actor(key).orElse(null);
            if (control == null || control.status() != ThreatActorControlState.Status.ACTIVE
                    || !entity.getUUID().equals(control.entityId())) {
                return ActorDamageResult.blocked("Threat controller has no active PM combat lease");
            }
            boolean defeated = data.threatCombat().applyActorDamage(key, Math.max(1, (int) Math.ceil(amount))) == 0;
            provider.presentThreatControllerDamage(entity, defeated); data.setDirty();
            if (!defeated) return ActorDamageResult.consumed();
            entity.discard();
            String causation = "combat:" + entity.getUUID();
            observations.accept(new ThreatControllerDestroyed("controller-destroyed:" + causation, facilityId, causation));
            return ActorDamageResult.defeated();
        } catch (IllegalArgumentException invalid) {
            return ActorDamageResult.blocked("Threat controller contains invalid provenance");
        }
    }
}
