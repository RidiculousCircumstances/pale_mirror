package io.farfrontier.palemirror.internal.integration.spore;

import java.util.Comparator;

import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.effect.ControlledEffectExecutor;
import io.farfrontier.palemirror.internal.effect.EffectLease;
import io.farfrontier.palemirror.internal.combat.PmProjectileRuntime;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * PM's small stationary-combat executor for the audited Spore roster.  It
 * never enables native goals, navigation, projectiles, evolution, terrain
 * work or infection.  The encounter record owns cooldowns; Spore supplies
 * only the registered model and sound resources.
 */
final class SporeCombatRuntime {
    private static final int WORK_BUDGET_PER_TICK = 8;

    void tick(MinecraftServer server, PaleMirrorSavedData data, SporeSandboxAdapter adapter) {
        int remaining = WORK_BUDGET_PER_TICK;
        long gameTick = server.overworld().getGameTime();
        for (TestMineRecord site : data.testMines().values().stream().sorted(Comparator.comparing(TestMineRecord::id)).toList()) {
            if (remaining == 0) return;
            ServerLevel level = levelFor(server, site);
            if (level == null) continue;
            for (EncounterActorRef reference : site.encounter().actors()) {
                if (remaining == 0) return;
                if (reference.status() != EncounterActorRef.Status.ACTIVE || reference.entityId() == null) continue;
                Entity entity = level.getEntity(reference.entityId());
                SporeActorProfile profile = SporeActorProfile.byId(reference.actorProfileId()).orElse(null);
                if (!(entity instanceof Mob actor) || profile == null
                        || !adapter.matchesOwnedActor(entity, site, reference.slotId())) continue;
                adapter.holdConstrained(actor);
                if (reference.nextRuntimeTick() > gameTick) continue;
                ServerPlayer target = target(level, site, actor, profile);
                if (target == null) continue;
                actor.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
                if (profile == SporeActorProfile.SPITTER) {
                    PmProjectileRuntime.launchVisualCarrier(data, level, "spore", site.id().value(),
                            io.farfrontier.palemirror.internal.combat.ThreatCombatLedger.actorKey("spore", site.id().value(),
                                    "encounter", reference.slotId()), reference.slotId(), actor, target, "spore:acid_ball",
                            adapter.acidBallType(), SporeSandboxAdapter.PROJECTILE_ROLE, profile.attackDamage(), gameTick,
                            reference.actionCounter());
                } else {
                    boolean[] landed = {false};
                    String key = "spore:attack:" + site.id().value() + ":" + reference.slotId() + ":"
                            + actor.getUUID() + ":" + gameTick;
                    EffectLease lease = EffectLease.planned("pm:" + key, key, "spore", site.id().value(), reference.slotId(),
                            "direct_attack", gameTick, gameTick + profile.attackCooldownTicks());
                    ControlledEffectExecutor.executeOnce(data, lease, gameTick, () -> {
                        landed[0] = target.hurt(level.damageSources().mobAttack(actor), profile.attackDamage());
                        level.playSound(null, actor.blockPosition(), profile.attackSound(), SoundSource.HOSTILE, 0.8F, 1.0F);
                        if (landed[0]) {
                            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                                    target.getZ(), 5, 0.2D, 0.3D, 0.2D, 0.01D);
                        }
                    });
                }
                site.encounter().scheduleRuntime(reference.slotId(), gameTick + profile.attackCooldownTicks());
                data.setDirty();
                remaining--;
            }
        }
    }

    private static ServerPlayer target(ServerLevel level, TestMineRecord site, Mob actor, SporeActorProfile profile) {
        double rangeSquared = profile.attackRange() * profile.attackRange();
        return level.players().stream()
                .filter(player -> !player.isSpectator() && site.contains(player.blockPosition()))
                .filter(player -> actor.distanceToSqr(player) <= rangeSquared)
                .min(Comparator.comparingDouble(actor::distanceToSqr))
                .orElse(null);
    }

    private static ServerLevel levelFor(MinecraftServer server, TestMineRecord site) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(site.dimensionId())) return level;
        }
        return null;
    }
}
