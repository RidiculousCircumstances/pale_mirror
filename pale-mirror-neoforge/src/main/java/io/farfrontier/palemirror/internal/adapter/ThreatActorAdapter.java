package io.farfrontier.palemirror.internal.adapter;

import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import io.farfrontier.palemirror.internal.integration.ActorDamageResult;
import io.farfrontier.palemirror.internal.integration.ActorOperationResult;
import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * Internal black-box boundary for optional native encounter forms.  It owns
 * neither the scenario nor the facility: it only represents an already
 * persisted PM slot and reports whether that representation is present.
 */
public interface ThreatActorAdapter extends io.farfrontier.palemirror.api.IntegrationAdapter {
    InfectionSourceId source();
    ActorOperationResult ensureActor(ServerLevel level, TestMineRecord site, String jobId, EncounterProfile.ActorSlot slot);
    ActorOperationResult removeActor(ServerLevel level, TestMineRecord site, String slotId);
    boolean matchesActor(Entity entity);
    boolean matchesOwnedActor(Entity entity, TestMineRecord site, String slotId);

    /**
     * Classifies damage to one persisted PM actor.  Native integrations must
     * return BLOCKED unless they have an audited safe combat policy.
     */
    default ActorDamageResult receiveDamage(ServerLevel level, TestMineRecord site, Entity entity,
                                            EncounterActorRef reference, DamageSource source, float amount) {
        return ActorDamageResult.passThrough();
    }
    default void tickRuntime(MinecraftServer server, PaleMirrorSavedData data) { }
    default void presentDamage(net.minecraft.world.entity.LivingEntity entity) { }
    default void presentDeath(net.minecraft.world.entity.LivingEntity entity) { }
    default void presentAttack(Entity entity) { }
}
