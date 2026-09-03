package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResidentProfile;
import net.minecraft.world.phys.Vec3;

/** Pure ambient-idle target grammar; directed work keeps its retained lease target. */
final class FrontierV3AmbientActorLocalTargets {
    private FrontierV3AmbientActorLocalTargets() { }

    static Vec3 localTarget(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease, long gameTime) {
        if (directedGoal(lease)) {
            BlockPosition target = lease.goalBody().supportingSurface().support();
            return new Vec3(target.x() + 0.5D, target.y(), target.z() + 0.5D);
        }
        LocalBrain brain = localBrain(state, actorId);
        long cycle = Math.floorMod(gameTime, brain.periodTicks());
        double phase = (cycle / (double) brain.periodTicks()) + (brain.identityPhase() / 16.0D);
        double angle = phase * Math.PI * 2.0D;
        BlockPosition anchor = lease.handoffBody().supportingSurface().support();
        return new Vec3(anchor.x() + 0.5D + Math.cos(angle) * brain.radius(), anchor.y(), anchor.z() + 0.5D + Math.sin(angle) * brain.radius());
    }

    static boolean directedGoal(AmbientActorLease lease) {
        return lease.goal() == AmbientGoalKind.TRANSIT || lease.goal() == AmbientGoalKind.OPERATION_ASSEMBLY
                || lease.goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY || lease.goal() == AmbientGoalKind.HIVE_TASK_ASSEMBLY
                || lease.goal() == AmbientGoalKind.SCOUT_PATROL;
    }

    private static LocalBrain localBrain(FrontierWorldState state, SubjectId actorId) {
        int identityPhase = Math.floorMod(actorId.value().hashCode(), 16);
        ResidentProfile resident = state.humanPopulation().resident(actorId);
        if (resident != null) return switch (resident.role()) {
            case FARMER -> new LocalBrain(1.00D, 360L, identityPhase);
            case BUILDER -> new LocalBrain(1.25D, 300L, identityPhase);
            case CRAFTER -> new LocalBrain(0.75D, 280L, identityPhase);
            case GUARD -> new LocalBrain(1.50D, 480L, identityPhase);
            case MEDIC -> new LocalBrain(0.50D, 240L, identityPhase);
            case HAULER -> new LocalBrain(1.50D, 320L, identityPhase);
        };
        Bioform bioform = FrontierV3AmbientActorExecutor.bioformProfile(state, actorId);
        if (bioform.isScout()) return new LocalBrain(1.50D, 240L, identityPhase);
        if (bioform.isExplosiveAssaulter()) return new LocalBrain(1.50D, 300L, identityPhase);
        if (bioform.isDefender()) return new LocalBrain(1.25D, 480L, identityPhase);
        return new LocalBrain(1.00D, 320L, identityPhase);
    }

    private record LocalBrain(double radius, long periodTicks, int identityPhase) { }
}
