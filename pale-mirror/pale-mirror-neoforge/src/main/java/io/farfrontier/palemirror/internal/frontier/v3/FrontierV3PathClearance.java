package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

/** Shared block-only body clearance for native edges and the actuator's actual next target.
 * Living pedestrians remain the traffic owner's concern, never solid terrain. */
final class FrontierV3PathClearance {
    private static final double SAMPLE_DISTANCE = 0.25;
    private FrontierV3PathClearance() { }

    static Vec3 point(ServerLevel level, Mob actor, BlockPos node) {
        double offset = (int) (actor.getBbWidth() + 1.0F) * 0.5;
        return new Vec3(node.getX() + offset, level.hasChunkAt(node) ? WalkNodeEvaluator.getFloorLevel(level, node) : node.getY(), node.getZ() + offset);
    }
    static boolean path(ServerLevel level, Mob actor, Path path) {
        if (path == null || path.getNodeCount() > FrontierV3PhysicalPathPolicy.MAX_PATH_NODES) return false;
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            var next = point(level, actor, path.getNode(i).asBlockPos());
            var from = i == path.getNextNodeIndex() ? actor.position() : point(level, actor, path.getNode(i - 1).asBlockPos());
            if (!move(level, actor, from, next)) return false;
        }
        return true;
    }
    static boolean edge(ServerLevel level, Mob actor, BlockPos from, BlockPos to) {
        return move(level, actor, point(level, actor, from), point(level, actor, to));
    }
    static boolean move(ServerLevel level, Mob actor, Vec3 from, Vec3 to) {
        // Stepping up needs headroom above the lower body before it can cross the riser.
        // Stepping down crosses the ledge at the higher height before landing below it.
        var corner = to.y > from.y ? new Vec3(from.x, to.y, from.z) : new Vec3(to.x, from.y, to.z);
        return sweep(level, actor, from, corner) && sweep(level, actor, corner, to);
    }
    private static boolean sweep(ServerLevel level, Mob actor, Vec3 from, Vec3 to) {
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / SAMPLE_DISTANCE));
        if (steps > FrontierV3PhysicalPathPolicy.MAX_PATH_NODES * 8) return false;
        for (int i = 0; i <= steps; i++) {
            var p = from.lerp(to, (double) i / steps);
            var box = actor.getBoundingBox().move(p.subtract(actor.position())).deflate(1.0e-6);
            for (int x = net.minecraft.util.Mth.floor(box.minX); x <= net.minecraft.util.Mth.floor(box.maxX); x++)
                for (int z = net.minecraft.util.Mth.floor(box.minZ); z <= net.minecraft.util.Mth.floor(box.maxZ); z++)
                    if (!level.hasChunkAt(new BlockPos(x, net.minecraft.util.Mth.floor(box.minY), z))) return false;
            if (level.getBlockCollisions(actor, box).iterator().hasNext()) return false;
        }
        return true;
    }
}
