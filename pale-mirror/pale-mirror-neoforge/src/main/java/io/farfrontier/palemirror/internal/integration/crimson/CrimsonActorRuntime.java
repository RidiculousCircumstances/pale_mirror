package io.farfrontier.palemirror.internal.integration.crimson;

import java.util.Comparator;

import io.farfrontier.palemirror.internal.combat.PmProjectileRuntime;
import io.farfrontier.palemirror.internal.combat.ThreatActorControlState;
import io.farfrontier.palemirror.internal.combat.ThreatCombatLedger;
import io.farfrontier.palemirror.internal.effect.ControlledEffectExecutor;
import io.farfrontier.palemirror.internal.effect.EffectLease;
import io.farfrontier.palemirror.internal.world.EncounterActorRef;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Fully PM-owned movement and attack loop for registered normal Crimson forms. */
final class CrimsonActorRuntime {
    private static final int WORK_BUDGET_PER_TICK = 32;
    private static final double TARGET_RANGE_SQUARED = 32.0D * 32.0D;
    private final CrimsonPresentationRuntime presentation;

    CrimsonActorRuntime(CrimsonPresentationRuntime presentation) {
        this.presentation = presentation;
    }

    void tick(MinecraftServer server, PaleMirrorSavedData data) {
        int remaining = WORK_BUDGET_PER_TICK;
        long gameTick = server.overworld().getGameTime();
        for (TestMineRecord mine : data.testMines().values().stream().sorted(Comparator.comparing(TestMineRecord::id)).toList()) {
            if (remaining == 0) return;
            ServerLevel level = levelFor(server, mine);
            if (level == null) continue;
            for (EncounterActorRef reference : mine.encounter().actors()) {
                if (remaining == 0) return;
                if (reference.status() != EncounterActorRef.Status.ACTIVE || reference.entityId() == null) continue;
                Entity entity = level.getEntity(reference.entityId());
                CrimsonActorProfile profile = CrimsonActorProfile.byId(reference.actorProfileId()).orElse(null);
                if (!(entity instanceof Mob actor) || profile == null
                        || !CrimsonSandboxAdapter.isOwnedActor(entity, mine, reference.slotId()) || !profile.matches(entity)) continue;
                CrimsonCombatProfile combat = CrimsonCombatProfile.forActor(profile);
                String key = ThreatCombatLedger.actorKey("crimson", mine.id().value(), "encounter", reference.slotId());
                ThreatActorControlState state = data.threatCombat().attachActor("crimson", mine.id().value(), "encounter",
                        reference.slotId(), profile.id(), actor.getUUID(), combat.hitPoints());
                holdControlled(actor);
                if (state.status() != ThreatActorControlState.Status.ACTIVE) continue;
                keepInsideThreatSite(mine, actor);
                ServerPlayer target = nearestTarget(level, mine, actor);
                if (target != null && state.nextMovementTick() <= gameTick) {
                    moveToward(actor, target, combat.movementPerTick());
                    data.threatCombat().scheduleActorMovement(key, gameTick + 1L, state.routeCursor() + 1);
                    data.setDirty();
                }
                // EncounterRecord remains the durable scheduler for an actor's
                // public encounter lifecycle. The combat ledger mirrors it for
                // diagnostics/restart recovery; it must not ignore an explicit
                // PM reschedule (including a scenario/runtime reconciliation).
                if (target != null && reference.nextRuntimeTick() <= gameTick) {
                    execute(data, mine, reference, actor, target, profile, combat, gameTick);
                    long nextAction = gameTick + combat.attackCooldown();
                    mine.encounter().scheduleRuntime(reference.slotId(), nextAction);
                    data.threatCombat().scheduleActorAction(key, nextAction);
                    data.setDirty();
                }
                remaining--;
            }
        }
    }

    static void holdControlled(Mob actor) {
        actor.setNoAi(true);
        actor.setTarget(null);
        actor.setLastHurtByMob(null);
        actor.getNavigation().stop();
    }

    private void execute(PaleMirrorSavedData data, TestMineRecord mine, EncounterActorRef reference, Mob actor,
                         ServerPlayer target, CrimsonActorProfile profile, CrimsonCombatProfile combat, long gameTick) {
        switch (combat.mode()) {
            case MELEE -> melee(data, mine, reference, actor, target, profile, combat, gameTick);
            case ARROW -> arrow(data, mine, reference, actor, target, profile, combat, gameTick);
            case RUSHER_DASH -> dash(data, mine, reference, actor, target, profile, combat, gameTick);
            case RAPTOR_AURA -> raptor(data, mine, reference, actor, target, profile, combat, gameTick);
        }
    }

    private void melee(PaleMirrorSavedData data, TestMineRecord mine, EncounterActorRef reference, Mob actor,
                       ServerPlayer target, CrimsonActorProfile profile, CrimsonCombatProfile combat, long gameTick) {
        if (actor.distanceToSqr(target) > combat.attackRange() * combat.attackRange()) return;
        effect(data, mine, reference, actor, "melee", gameTick, () -> {
            target.hurt(actor.level().damageSources().mobAttack(actor), combat.damage());
            presentation.attacked(actor, profile.id());
        });
    }

    private void arrow(PaleMirrorSavedData data, TestMineRecord mine, EncounterActorRef reference, Mob actor,
                       ServerPlayer target, CrimsonActorProfile profile, CrimsonCombatProfile combat, long gameTick) {
        if (actor.distanceToSqr(target) > combat.attackRange() * combat.attackRange()) return;
        if (PmProjectileRuntime.launchArrow(data, (ServerLevel) actor.level(), "crimson", mine.id().value(),
                ThreatCombatLedger.actorKey("crimson", mine.id().value(), "encounter", reference.slotId()), reference.slotId(),
                actor, target, profile.visualName(), combat.damage(), gameTick, reference.actionCounter())) presentation.attacked(actor, profile.id());
    }

    private void dash(PaleMirrorSavedData data, TestMineRecord mine, EncounterActorRef reference, Mob actor,
                      ServerPlayer target, CrimsonActorProfile profile, CrimsonCombatProfile combat, long gameTick) {
        Vec3 delta = target.position().subtract(actor.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 4.0D || horizontal > 20.0D) return;
        effect(data, mine, reference, actor, "dash", gameTick, () -> {
            actor.setDeltaMovement(delta.x / horizontal * 0.8D, 0.25D, delta.z / horizontal * 0.8D);
            actor.hurtMarked = true;
            actor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 1, true, false));
            presentation.rusherDash(actor);
        });
    }

    private void raptor(PaleMirrorSavedData data, TestMineRecord mine, EncounterActorRef reference, Mob actor,
                        ServerPlayer target, CrimsonActorProfile profile, CrimsonCombatProfile combat, long gameTick) {
        effect(data, mine, reference, actor, "raptor_aura", gameTick, () -> {
            actor.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, true, false));
            if (actor.distanceToSqr(target) <= 1.8D * 1.8D) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
            }
            presentation.raptorAura(actor);
        });
    }

    private static void effect(PaleMirrorSavedData data, TestMineRecord mine, EncounterActorRef reference, Mob actor,
                               String kind, long gameTick, Runnable action) {
        String id = "pm:crimson:" + kind + ":" + mine.id().value() + ":" + reference.slotId() + ":" + actor.getUUID()
                + ":" + gameTick + ":" + reference.actionCounter();
        ControlledEffectExecutor.executeOnce(data, EffectLease.planned(id, id, "crimson", mine.id().value(), reference.slotId(),
                kind, gameTick, gameTick + 120L), gameTick, action);
    }

    private static ServerPlayer nearestTarget(ServerLevel level, TestMineRecord mine, Mob actor) {
        return level.players().stream().filter(player -> !player.isSpectator() && mine.contains(player.blockPosition()))
                .filter(player -> actor.distanceToSqr(player) <= TARGET_RANGE_SQUARED)
                .min(Comparator.comparingDouble(actor::distanceToSqr)).orElse(null);
    }

    private static void moveToward(Mob actor, ServerPlayer target, double speed) {
        if (speed <= 0.0D) return;
        Vec3 delta = target.position().subtract(actor.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 2.2D) return;
        actor.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        actor.move(MoverType.SELF, new Vec3(delta.x / horizontal * speed, 0.0D, delta.z / horizontal * speed));
    }

    private static void keepInsideThreatSite(TestMineRecord mine, Mob actor) {
        if (mine.contains(actor.blockPosition())) return;
        actor.moveTo(Mth.clamp(actor.getX(), mine.object().minBounds().getX() + 0.5D, mine.object().maxBounds().getX() + 0.5D),
                Mth.clamp(actor.getY(), mine.object().minBounds().getY() + 1.0D, mine.object().maxBounds().getY() + 1.0D),
                Mth.clamp(actor.getZ(), mine.object().minBounds().getZ() + 0.5D, mine.object().maxBounds().getZ() + 0.5D),
                actor.getYRot(), actor.getXRot());
        actor.setDeltaMovement(Vec3.ZERO);
    }

    private static ServerLevel levelFor(MinecraftServer server, TestMineRecord mine) {
        for (ServerLevel level : server.getAllLevels()) if (level.dimension().location().toString().equals(mine.dimensionId())) return level;
        return null;
    }
}
