package io.farfrontier.palemirror.internal.integration.crimson;

import java.util.Comparator;

import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.SiegePartRef;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.effect.ControlledEffectExecutor;
import io.farfrontier.palemirror.internal.effect.EffectLease;
import io.farfrontier.palemirror.internal.combat.ThreatActorControlState;
import io.farfrontier.palemirror.internal.combat.ThreatCombatLedger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded combat behavior for PM's local Crimson forms.  It intentionally has
 * no Crimson scoreboard, no world scans and no terrain-editing actions.
 */
final class CrimsonSiegeRuntime {
    private static final int WORK_BUDGET_PER_TICK = 8;
    private static final double TARGET_RANGE_SQUARED = 32.0D * 32.0D;
    private final CrimsonPresentationRuntime presentation;

    CrimsonSiegeRuntime(CrimsonPresentationRuntime presentation) {
        this.presentation = presentation;
    }

    void tick(MinecraftServer server, PaleMirrorSavedData data) {
        int remaining = WORK_BUDGET_PER_TICK;
        long gameTick = server.overworld().getGameTime();
        for (TestMineRecord mine : data.testMines().values().stream().sorted(Comparator.comparing(TestMineRecord::id)).toList()) {
            ServerLevel level = levelFor(server, mine);
            if (level == null) continue;
            for (SiegePartRef part : mine.siege().parts()) {
                if (remaining == 0) return;
                if (part.kind() == io.farfrontier.palemirror.internal.world.SiegePartKind.NODE
                        || part.status() != SiegePartRef.Status.ACTIVE || part.entityId() == null) continue;
                Entity entity = level.getEntity(part.entityId());
                CrimsonSiegeProfile profile = CrimsonSiegeProfile.byId(part.profileId()).orElse(null);
                if (!(entity instanceof Mob actor) || profile == null
                        || !CrimsonSandboxAdapter.isOwnedSiegeEntity(entity, mine, part.slotId()) || !profile.matches(entity)) continue;
                CrimsonCombatProfile combat = CrimsonCombatProfile.forSiege(profile);
                String controlKey = ThreatCombatLedger.actorKey("crimson", mine.id().value(), "siege", part.slotId());
                ThreatActorControlState state = data.threatCombat().attachActor("crimson", mine.id().value(), "siege", part.slotId(),
                        profile.id(), actor.getUUID(), combat.hitPoints());
                CrimsonActorRuntime.holdControlled(actor);
                if (state.status() != ThreatActorControlState.Status.ACTIVE) continue;
                keepInsideThreatSite(mine, actor);
                ServerPlayer target = nearestTarget(level, mine, actor);
                if (target != null && combat.movementPerTick() > 0.0D && state.nextMovementTick() <= gameTick) {
                    moveToward(actor, target, combat.movementPerTick());
                    data.threatCombat().scheduleActorMovement(controlKey, gameTick + 1L, state.routeCursor() + 1);
                    data.setDirty();
                }
                if (target != null && state.nextActionTick() <= gameTick) {
                    execute(profile, combat, state, data, mine, part, actor, target, gameTick, presentation);
                    data.threatCombat().scheduleActorAction(controlKey, gameTick + combat.attackCooldown());
                    data.setDirty();
                }
                remaining--;
            }
        }
    }

    private static void execute(CrimsonSiegeProfile profile, CrimsonCombatProfile combat, ThreatActorControlState state,
                                PaleMirrorSavedData data, TestMineRecord mine, SiegePartRef part,
                                Mob actor, ServerPlayer target, long gameTick,
                                CrimsonPresentationRuntime presentation) {
        switch (profile) {
            case MANGLER -> dash(data, mine, part, actor, target, gameTick, presentation);
            case PUMMELER -> rangedPulse(data, mine, part, actor, target, gameTick, presentation);
            case KRAKEN -> grasp(data, mine, part, actor, target, gameTick, presentation);
            case OSIRIS -> {
                phase(data, mine, part, actor, combat, state, gameTick);
                melee(data, mine, part, actor, target, gameTick, presentation);
            }
            case BLOODLINK_I, BLOODLINK_II, BLOODLINK_III -> bloodlinkAura(data, mine, part, actor, target, gameTick, presentation);
            case JUGGERNAUT, KNIGHT -> melee(data, mine, part, actor, target, gameTick, presentation);
        }
    }

    private static void melee(PaleMirrorSavedData data, TestMineRecord mine, SiegePartRef part, Mob actor,
                              ServerPlayer target, long gameTick, CrimsonPresentationRuntime presentation) {
        CrimsonCombatProfile stats = CrimsonCombatProfile.forSiege(CrimsonSiegeProfile.byId(part.profileId()).orElseThrow());
        if (actor.distanceToSqr(target) > stats.attackRange() * stats.attackRange()) return;
        executeOnce(data, mine, part, actor, "melee", gameTick, stats.attackCooldown(), () -> {
            target.hurt(actor.level().damageSources().mobAttack(actor), stats.damage());
            presentation.attacked(actor, part.profileId());
        });
    }

    private static void dash(PaleMirrorSavedData data, TestMineRecord mine, SiegePartRef part, Mob actor, ServerPlayer target,
                             long gameTick, CrimsonPresentationRuntime presentation) {
        Vec3 delta = target.position().subtract(actor.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 4.0D || horizontal > 20.0D) return;
        executeOnce(data, mine, part, actor, "dash", gameTick, 40L, () -> {
            actor.setDeltaMovement(delta.x / horizontal * 0.8D, 0.25D, delta.z / horizontal * 0.8D);
            presentation.manglerDash(actor);
        });
    }

    private static void rangedPulse(PaleMirrorSavedData data, TestMineRecord mine, SiegePartRef part, Mob actor, ServerPlayer target,
                                    long gameTick, CrimsonPresentationRuntime presentation) {
        if (actor.distanceToSqr(target) <= 24.0D * 24.0D) {
            executeOnce(data, mine, part, actor, "pummeler_pulse", gameTick, 60L, () -> {
                target.hurt(actor.level().damageSources().mobAttack(actor), 6.0F);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, false));
                presentation.pummelerPulse(actor, target);
            });
        }
    }

    private static void grasp(PaleMirrorSavedData data, TestMineRecord mine, SiegePartRef part, Mob actor, ServerPlayer target,
                              long gameTick, CrimsonPresentationRuntime presentation) {
        if (actor.distanceToSqr(target) <= 8.0D * 8.0D) {
            executeOnce(data, mine, part, actor, "kraken_grasp", gameTick, 40L, () -> {
                target.hurt(actor.level().damageSources().mobAttack(actor), 5.0F);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, true, false));
                presentation.krakenGrasp(actor, target);
            });
        }
    }

    private static void phase(PaleMirrorSavedData data, TestMineRecord mine, SiegePartRef part, Mob actor,
                              CrimsonCombatProfile combat, ThreatActorControlState state, long gameTick) {
        executeOnce(data, mine, part, actor, "osiris_phase", gameTick, 40L, () -> {
            // Native health is presentation-only: all incoming damage was
            // intercepted before it could modify this entity. The PM combat
            // ledger is therefore the sole valid input for a phase change.
            float ratio = (float) state.hitPoints() / combat.hitPoints();
            int amplifier = ratio <= 0.25F ? 2 : ratio <= 0.50F ? 1 : 0;
            actor.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, amplifier, true, false));
        });
    }

    private static void bloodlinkAura(PaleMirrorSavedData data, TestMineRecord mine, SiegePartRef part, Mob actor,
                                      ServerPlayer target, long gameTick, CrimsonPresentationRuntime presentation) {
        if (actor.distanceToSqr(target) <= 10.0D * 10.0D) {
            executeOnce(data, mine, part, actor, "bloodlink_aura", gameTick, 40L, () -> {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, true, false));
                presentation.bloodlinkAura(actor);
            });
        }
    }

    private static void executeOnce(PaleMirrorSavedData data, TestMineRecord mine, SiegePartRef part, Mob actor,
                                    String kind, long gameTick, long ttl, Runnable action) {
        String key = "crimson:" + kind + ":" + mine.id().value() + ":" + part.slotId() + ":" + actor.getUUID()
                + ":" + gameTick;
        ControlledEffectExecutor.executeOnce(data, EffectLease.planned("pm:" + key, key, "crimson", mine.id().value(),
                part.slotId(), kind, gameTick, gameTick + ttl), gameTick, action);
    }

    private static ServerPlayer nearestTarget(ServerLevel level, TestMineRecord mine, Mob actor) {
        return level.players().stream().filter(player -> !player.isSpectator() && mine.contains(player.blockPosition()))
                .filter(player -> actor.distanceToSqr(player) <= TARGET_RANGE_SQUARED)
                .min(Comparator.comparingDouble(actor::distanceToSqr)).orElse(null);
    }

    private static void keepInsideThreatSite(TestMineRecord mine, Mob actor) {
        if (mine.contains(actor.blockPosition())) return;
        int minX = mine.object().minBounds().getX();
        int maxX = mine.object().maxBounds().getX();
        int minY = mine.object().minBounds().getY();
        int maxY = mine.object().maxBounds().getY();
        int minZ = mine.object().minBounds().getZ();
        int maxZ = mine.object().maxBounds().getZ();
        actor.moveTo(Mth.clamp(actor.getX(), minX + 0.5D, maxX + 0.5D),
                Mth.clamp(actor.getY(), minY + 1.0D, maxY + 1.0D),
                Mth.clamp(actor.getZ(), minZ + 0.5D, maxZ + 0.5D), actor.getYRot(), actor.getXRot());
        actor.setDeltaMovement(Vec3.ZERO);
        actor.getNavigation().stop();
    }

    private static void moveToward(Mob actor, ServerPlayer target, double speed) {
        Vec3 delta = target.position().subtract(actor.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 2.2D) return;
        actor.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        actor.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(delta.x / horizontal * speed, 0.0D,
                delta.z / horizontal * speed));
    }

    private static ServerLevel levelFor(MinecraftServer server, TestMineRecord mine) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(mine.dimensionId())) return level;
        }
        return null;
    }
}
