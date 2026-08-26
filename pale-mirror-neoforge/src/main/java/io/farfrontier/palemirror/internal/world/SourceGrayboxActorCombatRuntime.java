package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.internal.effect.ControlledEffectExecutor;
import io.farfrontier.palemirror.internal.effect.EffectLease;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Bounded real-time melee execution for already HOT source actors.
 *
 * <p>This runtime selects only an immediate local target; source strategy,
 * deployment and life/death remain elsewhere. Every attempted hit first
 * reserves a durable actor epoch and then executes through an effect lease,
 * so a server loss after the physical call produces an explicit unknown
 * receipt rather than replaying damage.</p>
 */
final class SourceGrayboxActorCombatRuntime {
    private static final long INTERVAL_TICKS = 5L;
    private static final int WORK_BUDGET_PER_INTERVAL = 64;
    private static final long COOLDOWN_TICKS = 20L;
    private static final double ATTACK_RANGE = 1.9d;
    private static final float GUARD_DAMAGE = 2.0f;
    private static final float HIVE_DAMAGE = 1.5f;
    private int cursor;

    void tick(ServerLevel level, SourceGrayboxCombatOwner data, SourceGrayboxMaterializer materializer,
              Map<String, Entity> admittedEntities, long observedGameTick) {
        if (observedGameTick % INTERVAL_TICKS != 0L) return;
        ReferenceGrayboxSnapshot snapshot = data.snapshot();
        List<SourceGrayboxActorBehaviorRuntime.ActorView> physical = SourceGrayboxActorBehaviorRuntime.physicalActors(
                level, snapshot, data.actorExecution(), materializer, admittedEntities);
        if (physical.isEmpty()) return;
        int processed = Math.min(WORK_BUDGET_PER_INTERVAL, physical.size());
        int start = Math.floorMod(cursor, physical.size());
        cursor = Math.floorMod(start + processed, physical.size());
        long actionTick = data.actorExecutionGameTime(observedGameTick);
        for (int offset = 0; offset < processed; offset++) {
            SourceGrayboxActorBehaviorRuntime.ActorView actor = physical.get((start + offset) % physical.size());
            if (actor.body().isRemoved() || !actor.body().isAlive()) continue;
            LivingEntity target = target(level, actor, physical);
            if (target == null || target.isRemoved() || !target.isAlive()) continue;
            attemptMelee(data, snapshot, actor, target, actionTick);
        }
    }

    private static LivingEntity target(ServerLevel level, SourceGrayboxActorBehaviorRuntime.ActorView actor,
                                       List<SourceGrayboxActorBehaviorRuntime.ActorView> physical) {
        if (actor.kind() == ReferenceGrayboxActorExecutionState.ActorKind.RESIDENT) {
            if (!SourceGrayboxActorBehaviorRuntime.guard(actor.resident())) return null;
            return physical.stream()
                    .filter(candidate -> candidate.kind() == ReferenceGrayboxActorExecutionState.ActorKind.BIOFORM)
                    .filter(candidate -> candidate.body().isAlive() && inRange(actor.body(), candidate.body()))
                    .min(Comparator.comparingDouble(candidate -> actor.body().distanceToSqr(candidate.body())))
                    .map(SourceGrayboxActorBehaviorRuntime.ActorView::body).orElse(null);
        }
        if (!pursuing(actor.bioform())) return null;
        return level.getEntitiesOfClass(LivingEntity.class, actor.body().getBoundingBox().inflate(ATTACK_RANGE),
                        candidate -> candidate != actor.body() && candidate.isAlive() && !managedBioform(candidate))
                .stream().min(Comparator.comparingDouble(candidate -> actor.body().distanceToSqr(candidate))).orElse(null);
    }

    private static boolean pursuing(ReferenceGrayboxSnapshot.Bioform bioform) {
        return bioform.feral() || bioform.phase().equals("engaging") || bioform.phase().equals("advancing");
    }

    private static boolean managedBioform(Entity entity) {
        SourceGrayboxMaterializer.ManagedEntity managed = SourceGrayboxMaterializer.managed(entity);
        return managed != null && managed.kind().equals("BIOFORM");
    }

    private static boolean inRange(Entity first, Entity second) {
        return first.distanceToSqr(second) <= ATTACK_RANGE * ATTACK_RANGE;
    }

    private static void attemptMelee(SourceGrayboxCombatOwner data, ReferenceGrayboxSnapshot snapshot,
                                     SourceGrayboxActorBehaviorRuntime.ActorView actor, LivingEntity target, long actionTick) {
        ReferenceGrayboxActorExecutionState.ActorState state = actor.state();
        data.reserveActorCombat(state.id(), state.leaseId(), state.holder(), actionTick, COOLDOWN_TICKS).ifPresent(action -> {
            float beforeHealth = target.getHealth();
            boolean beforeAlive = target.isAlive();
            float damage = actor.kind() == ReferenceGrayboxActorExecutionState.ActorKind.RESIDENT ? GUARD_DAMAGE : HIVE_DAMAGE;
            EffectLease effect = EffectLease.planned(action.id(), action.id(), "reference-graybox", snapshot.profileId(),
                    action.actorId(), "melee", action.scheduledAtGameTick(), action.scheduledAtGameTick() + 1L);
            ControlledEffectExecutor.executeOnceWithReceipt(data, effect, action.scheduledAtGameTick(), target.getUUID(), () -> {
                actor.body().swing(InteractionHand.MAIN_HAND);
                boolean landed = target.hurt(actor.body().level().damageSources().mobAttack(actor.body()), damage);
                return receipt(target, beforeHealth, beforeAlive, landed);
            });
        });
    }

    private static String receipt(LivingEntity target, float beforeHealth, boolean beforeAlive, boolean landed) {
        return "target=" + target.getUUID() + ";landed=" + landed + ";beforeHealth16=" + fixed(beforeHealth)
                + ";afterHealth16=" + fixed(target.getHealth()) + ";beforeAlive=" + beforeAlive + ";afterAlive=" + target.isAlive();
    }

    private static int fixed(float value) {
        if (!Float.isFinite(value)) throw new IllegalStateException("physical combat health is not finite");
        return Math.round(value * 16.0f);
    }
}
