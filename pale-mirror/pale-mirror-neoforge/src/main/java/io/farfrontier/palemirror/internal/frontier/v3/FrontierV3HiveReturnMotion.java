package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilization;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationReturnAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;
import io.farfrontier.palemirror.frontier.v3.model.HiveTaskAssembly;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Physical adapter for the retained homeward cursor of one expedition survivor.
 * The parent state owns both membership and topology; this class observes one loaded edge only.
 */
final class FrontierV3HiveReturnMotion {
    private FrontierV3HiveReturnMotion() { }

    static boolean pursue(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                          FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        HiveMobilization mobilization = mobilization(state, actorId);
        HiveTaskAssembly.Member member = member(mobilization, actorId, lease);
        if (mobilization == null || member == null || member.arrived()
                || !mobilization.returnAssembly().orElseThrow().safeAdvances().contains(actorId)) {
            body.getNavigation().stop();
            return false;
        }
        BlockPos target = new BlockPos(lease.goalBody().x(), lease.goalBody().y(), lease.goalBody().z());
        if (!level.hasChunkAt(target)
                || !FrontierV3StandingPosition.hasExactStandingColumn(level, lease.goalBody().supportingSurface().support())) {
            body.getNavigation().stop();
            return false;
        }
        FrontierV3ControlledMobMotion.moveToward(level, body, new Vec3(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D));
        return false;
    }

    static boolean observeArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                  FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        HiveMobilization mobilization = mobilization(state, actorId);
        HiveTaskAssembly.Member member = member(mobilization, actorId, lease);
        if (mobilization == null || member == null || member.arrived()
                || !FrontierV3AmbientActorExecutor.observedBody(body).equals(lease.goalBody())
                || !mobilization.returnAssembly().orElseThrow().safeAdvances().contains(actorId)) {
            if (mobilization != null && member != null && !member.arrived()) body.getNavigation().stop();
            return false;
        }
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = FrontierV3AmbientActorExecutor.submit(runtime,
                "ambient-hive-return", actorId.value(), new HiveMobilizationReturnAdvanced(mobilization.id(), actorId, member.cursor()));
        FrontierV3DiagnosticTrace.record(level.getServer(), "hive-return:" + mobilization.id().value(),
                "hive_return_advanced", actorId, result);
        return true;
    }

    /** The draining boundary may retain only the current canonical return cursor. */
    static BodyPosition currentCursor(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        HiveTaskAssembly.Member member = member(mobilization(state, actorId), actorId, lease);
        return member == null ? null : member.currentSurface().standingBody();
    }

    private static HiveMobilization mobilization(FrontierWorldState state, SubjectId actorId) {
        return state.hiveColony().mobilizations().values().stream()
                .filter(value -> value.status() == HiveMobilizationStatus.RETURNING)
                .filter(value -> value.returnAssembly().map(assembly -> assembly.members().containsKey(actorId)).orElse(false))
                .reduce((left, right) -> { throw new IllegalStateException("ambient bioform belongs to more than one hive return"); })
                .orElse(null);
    }

    private static HiveTaskAssembly.Member member(HiveMobilization mobilization, SubjectId actorId, AmbientActorLease lease) {
        if (mobilization == null || lease.goal() != AmbientGoalKind.HIVE_TASK_RETURN) return null;
        HiveTaskAssembly.Member member = mobilization.returnAssembly().orElseThrow().members().get(actorId);
        SurfaceAnchor expected = member.arrived() ? member.currentSurface() : member.nextSurface();
        return lease.goalBody().equals(expected.standingBody()) ? member : null;
    }
}
