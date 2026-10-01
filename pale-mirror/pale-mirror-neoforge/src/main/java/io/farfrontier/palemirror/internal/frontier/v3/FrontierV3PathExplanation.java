package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;

/** Bounded read-only explanation of the already-computed physical candidate; never another search. */
final class FrontierV3PathExplanation {
    private FrontierV3PathExplanation() { }

    static String rejected(ServerLevel level, SurfaceAnchor target, Path path, FrontierV3NavigationScope scope) {
        var support = new net.minecraft.core.BlockPos(target.x(), target.y(), target.z());
        String prefix = "target=" + target.x() + "," + target.y() + "," + target.z()
                + ";targetSupport=" + BuiltInRegistries.BLOCK.getKey(level.getBlockState(support).getBlock())
                + ";targetFeet=" + BuiltInRegistries.BLOCK.getKey(level.getBlockState(support.above()).getBlock());
        if (path == null) return prefix + ";candidate=null";
        String result = prefix + ";reachable=" + path.canReach() + ";nodes=" + path.getNodeCount();
        if (path.getNodeCount() > 54) return result + ";nodeBoundExceeded=true";
        if (path.getNodeCount() > 0) result += ";end=" + path.getEndNode().asBlockPos().toShortString();
        for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
            var feet = path.getNode(index).asBlockPos();
            if (!level.hasChunkAt(feet)) return result + ";rejectedNode=" + index + ";unloaded=" + feet.toShortString();
            var nodeSupport = feet.below();
            boolean contained = scope.permits(new BlockPosition(nodeSupport.getX(), nodeSupport.getY(), nodeSupport.getZ()));
            boolean dry = level.getFluidState(feet).isEmpty();
            if (!contained || !dry) return result + ";rejectedNode=" + index + ";feet=" + feet.toShortString()
                    + ";insideTaskScope=" + contained + ";dry=" + dry + ";block="
                    + BuiltInRegistries.BLOCK.getKey(level.getBlockState(feet).getBlock());
        }
        return result + ";remainingNodesInsideTaskScope=true";
    }
}
