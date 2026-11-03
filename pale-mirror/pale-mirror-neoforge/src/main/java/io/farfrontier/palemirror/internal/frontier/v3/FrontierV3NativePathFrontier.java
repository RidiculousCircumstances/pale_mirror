package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A bounded proven physical approach is advice, never arrival at the original goal. */
final class FrontierV3NativePathFrontier {
    private FrontierV3NativePathFrontier() { }
    static List<SurfaceAnchor> hint(ServerLevel level, Mob body, Path candidate, BlockPos target,
            FrontierV3NavigationScope scope, Optional<UnitNavigationProfile> profile) {
        if (candidate == null || candidate.getNodeCount() < 2) return List.of();
        // A short blocked station is a real local failure: its caller must
        // retain ordinary alternatives/courtesy, not approach a dead end first.
        if (body.blockPosition().distManhattan(target) <= FrontierV3PhysicalPathPolicy.MAX_PATH_NODES
                && candidate.getNodeCount() <= FrontierV3PhysicalPathPolicy.MAX_PATH_NODES) return List.of();
        int first = candidate.getNextNodeIndex();
        int last = Math.min(candidate.getNodeCount() - 1, first + FrontierV3RouteNavigation.MAX_LEG_SUPPORTS);
        if (last <= first) return List.of();
        var nodes = new ArrayList<net.minecraft.world.level.pathfinder.Node>();
        for (int index = first; index <= last; index++) nodes.add(candidate.getNode(index));
        var end = nodes.getLast().asBlockPos();
        if (!advances(body.blockPosition(), end, target)) return List.of();
        // Only the observed prefix gets a local endpoint. This cannot turn the
        // incomplete original candidate into a complete semantic route.
        var prefix = new Path(nodes, end, true);
        if (FrontierV3PhysicalPathPolicy.reject(level, prefix, scope).isPresent()
                || !FrontierV3PedestrianTraffic.permitsProfile(prefix, profile)
                || !FrontierV3PathClearance.path(level, body, prefix)) return List.of();
        return nodes.stream().map(node -> SurfaceAnchor.at(node.x, node.y - 1, node.z)).toList();
    }
    static boolean advances(BlockPos origin, BlockPos end, BlockPos target) {
        return origin.distManhattan(target) > end.distManhattan(target) && !origin.equals(end);
    }
}
