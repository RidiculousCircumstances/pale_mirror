package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shared pedestrian acceptance policy; no activity stages, resource effects or route search. */
final class FrontierV3PhysicalPathPolicy {
    static final int MAX_PATH_NODES = 54; // Existing bounded physical-query safety maximum.
    record Rejection(FrontierV3GoalNavigation.BlockReason reason, String detail) { }
    private FrontierV3PhysicalPathPolicy() { }

    static Optional<Rejection> reject(ServerLevel level, Path path, FrontierV3NavigationScope scope) {
        if (path == null || path.getNodeCount() == 0 || !path.canReach())
            return Optional.of(new Rejection(FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE, "unreachable"));
        if (path.getNodeCount() > MAX_PATH_NODES)
            return Optional.of(new Rejection(FrontierV3GoalNavigation.BlockReason.SEARCH_BUDGET_EXHAUSTED, "node-bound"));
        for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
            var feet = path.getNode(index).asBlockPos();
            if (!level.hasChunkAt(feet))
                return Optional.of(new Rejection(FrontierV3GoalNavigation.BlockReason.TARGET_CHUNK_UNLOADED,
                        "unloaded-node:" + index));
            var support = feet.below();
            if (!scope.permits(new BlockPosition(support.getX(), support.getY(), support.getZ())))
                return Optional.of(new Rejection(FrontierV3GoalNavigation.BlockReason.OFF_CONTRACT,
                        "forbidden-node:" + index));
            if (!level.getFluidState(feet).isEmpty())
                return Optional.of(new Rejection(FrontierV3GoalNavigation.BlockReason.UNSUPPORTED_MEDIUM,
                        "fluid-node:" + index));
        }
        return Optional.empty();
    }

    /** A discardable corridor is derived AFTER acceptance, not permission supplied by the task. */
    static LocalNavigationEnvelope corridor(Path path, SurfaceAnchor target) {
        List<SurfaceAnchor> supports = new ArrayList<>();
        for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
            var support = path.getNode(index).asBlockPos().below();
            supports.add(SurfaceAnchor.at(support.getX(), support.getY(), support.getZ()));
        }
        if (supports.isEmpty()) supports.add(target);
        return LocalNavigationEnvelope.along(supports, List.of(target));
    }
}
