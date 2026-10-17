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
        var query = new Query(level, actor);
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            var next = query.point(path.getNode(i).asBlockPos());
            var from = i == path.getNextNodeIndex() ? actor.position() : query.point(path.getNode(i - 1).asBlockPos());
            if (!query.move(from, next)) return false;
        }
        return true;
    }
    static boolean edge(ServerLevel level, Mob actor, BlockPos from, BlockPos to) {
        return new Query(level, actor).edge(from, to);
    }
    static boolean move(ServerLevel level, Mob actor, Vec3 from, Vec3 to) {
        return new Query(level, actor).move(from, to);
    }

    /** One synchronous path query, never a cross-tick collision cache. Neighbour
     * expansion revisits the same nodes and edge samples; world/body facts cannot
     * change within the query. A later search observes player edits afresh. */
    static final class Query {
        private static final int MAX_POINTS = 32_768;
        private final ServerLevel level;
        private final Mob actor;
        private final Vec3 origin;
        private final net.minecraft.world.phys.AABB body;
        private final java.util.Map<BlockPos, Vec3> floors = new java.util.HashMap<>();
        private final java.util.Map<Vec3, Boolean> clearance = new java.util.HashMap<>();
        Query(ServerLevel level, Mob actor) {
            this.level = level; this.actor = actor;
            origin = actor.position(); body = actor.getBoundingBox();
        }
        Vec3 point(BlockPos node) {
            var cached = floors.get(node);
            if (cached != null) return cached;
            var value = FrontierV3PathClearance.point(level, actor, node);
            if (floors.size() < MAX_POINTS) floors.put(node.immutable(), value);
            return value;
        }
        boolean edge(BlockPos from, BlockPos to) { return move(point(from), point(to)); }
        boolean move(Vec3 from, Vec3 to) {
            // Stepping up needs headroom above the lower body before crossing the riser.
            // Stepping down crosses at the higher height before landing below it.
            var corner = to.y > from.y ? new Vec3(from.x, to.y, from.z) : new Vec3(to.x, from.y, to.z);
            return sweep(from, corner) && sweep(corner, to);
        }
        private boolean sweep(Vec3 from, Vec3 to) {
            int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / SAMPLE_DISTANCE));
            if (steps > FrontierV3PhysicalPathPolicy.MAX_PATH_NODES * 8) return false;
            for (int i = 0; i <= steps; i++) {
                var p = from.lerp(to, (double) i / steps);
                var cached = clearance.get(p);
                if (cached != null) { if (!cached) return false; continue; }
                boolean clear = clear(p);
                if (clearance.size() < MAX_POINTS) clearance.put(p, clear);
                if (!clear) return false;
            }
            return true;
        }
        private boolean clear(Vec3 p) {
            var box = body.move(p.subtract(origin)).deflate(1.0e-6);
            for (int x = net.minecraft.util.Mth.floor(box.minX); x <= net.minecraft.util.Mth.floor(box.maxX); x++)
                for (int z = net.minecraft.util.Mth.floor(box.minZ); z <= net.minecraft.util.Mth.floor(box.maxZ); z++)
                    if (!level.hasChunkAt(new BlockPos(x, net.minecraft.util.Mth.floor(box.minY), z))) return false;
            return !level.getBlockCollisions(actor, box).iterator().hasNext();
        }
    }
}
