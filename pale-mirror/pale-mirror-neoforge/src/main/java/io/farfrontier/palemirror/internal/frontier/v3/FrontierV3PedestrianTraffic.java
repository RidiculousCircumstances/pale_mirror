package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.Set;

/** Query-local living-body obstacles for native HOT pathfinding; never durable terrain. */
final class FrontierV3PedestrianTraffic {
    private static final int MAX_QUERY_RADIUS = FrontierV3PhysicalPathPolicy.MAX_PATH_NODES;
    private static final int MAX_VISITED_NODES = 4_096;
    private static final int MAX_BODIES = 256;
    private FrontierV3PedestrianTraffic() { }

    record Body(java.util.UUID id, AABB bounds) { }
    /** A clear native route rejected only by live occupants, not a guessed terrain failure. */
    record Query(Path path, List<Body> blockers, List<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> passage,
                 boolean budgetExhausted) {
        Query { blockers = List.copyOf(blockers); passage = List.copyOf(passage); }
        Query(Path path, List<Body> blockers, List<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> passage) {
            this(path, blockers, passage, false);
        }
        boolean trafficBlocked(ServerLevel level, FrontierV3NavigationScope scope) {
            return !blockers.isEmpty() && !budgetExhausted && FrontierV3PhysicalPathPolicy.reject(level, path, scope)
                    .filter(refused -> refused.reason() == FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE).isPresent();
        }
    }

    static List<Body> goalOccupants(ServerLevel level, Mob actor,
            List<io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor> stations) {
        return stations.stream().flatMap(station -> level.getEntitiesOfClass(LivingEntity.class,
                stationBodyAt(actor, new BlockPos(station.x(), station.y() + 1, station.z())),
                other -> other != actor && other.isAlive() && !other.isSpectator()).stream())
                .map(other -> new Body(other.getUUID(), other.getBoundingBox())).distinct()
                .sorted(java.util.Comparator.comparing(Body::id)).toList();
    }

    static boolean blockedAhead(ServerLevel level, Mob actor, Path path) {
        return blocked(level, actor, path, 2);
    }

    private static boolean blocked(ServerLevel level, Mob actor, Path path, int nodes) {
        if (path == null || path.isDone()) return false;
        int end = Math.min(path.getNodeCount(), path.getNextNodeIndex() + nodes);
        for (int index = path.getNextNodeIndex(); index < end; index++) {
            AABB body = nativeNodeBodyAt(actor, path.getNode(index).asBlockPos());
            if (!level.getEntitiesOfClass(LivingEntity.class, body,
                    other -> other != actor && other.isAlive() && !other.isSpectator()
                            && !following(actor, other, path)).isEmpty()) return true;
        }
        return false;
    }

    static Path createPath(ServerLevel level, Mob actor, BlockPos target, FrontierV3NavigationScope scope) {
        return query(level, actor, target, scope).path();
    }

    static Query query(ServerLevel level, Mob actor, BlockPos target, FrontierV3NavigationScope scope) {
        Path ordinary = actor.getNavigation().createPath(target, 0);
        // Only a scope-valid terrain path can establish that bodies caused this failure.
        if (FrontierV3PhysicalPathPolicy.reject(level, ordinary, scope).isPresent())
            return new Query(ordinary, List.of(), List.of());
        java.util.Map<java.util.UUID, Body> blockers = new java.util.LinkedHashMap<>();
        java.util.List<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> passage = new java.util.ArrayList<>();
        for (int index = ordinary.getNextNodeIndex(); index < ordinary.getNodeCount(); index++) {
            var feet = ordinary.getNode(index).asBlockPos();
            passage.add(new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(feet.getX(), feet.getY() - 1, feet.getZ()));
            for (var other : level.getEntitiesOfClass(LivingEntity.class, nativeNodeBodyAt(actor, feet),
                    entity -> entity != actor && entity.isAlive() && !entity.isSpectator() && !following(actor, entity, ordinary))) {
                if (blockers.size() >= MAX_BODIES) return new Query(null, List.of(), List.of(), true);
                blockers.putIfAbsent(other.getUUID(), new Body(other.getUUID(), other.getBoundingBox()));
            }
        }
        if (blockers.isEmpty()) return new Query(ordinary, List.of(), passage);
        // Use the same native graph and block/step semantics, adding only ephemeral body
        // clearance. The native region uses getChunkNow: no tickets or unloaded reads.
        int radius = Math.min(MAX_QUERY_RADIUS, Math.max(8,
                (int) actor.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) + 8));
        BlockPos origin = actor.blockPosition();
        AABB query = new AABB(origin.getX() - radius, origin.getY() - radius, origin.getZ() - radius,
                origin.getX() + radius + 1, origin.getY() + radius + 1, origin.getZ() + radius + 1);
        List<AABB> bodies = level.getEntitiesOfClass(LivingEntity.class, query,
                other -> other != actor && other.isAlive() && !other.isSpectator() && !following(actor, other, ordinary))
                .stream().limit(MAX_BODIES + 1L).map(LivingEntity::getBoundingBox).toList();
        if (bodies.size() > MAX_BODIES) return new Query(null, List.of(), List.of(), true);
        WalkNodeEvaluator evaluator = new WalkNodeEvaluator() {
            @Override public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
                BlockPos feet = new BlockPos(x, y, z);
                if (!level.hasChunkAt(feet) || !scope.permits(new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(x, y - 1, z)))
                    return PathType.BLOCKED;
                if (!feet.equals(origin) && bodies.stream().anyMatch(body -> body.intersects(nativeNodeBodyAt(mob, feet))))
                    return PathType.BLOCKED;
                return super.getPathTypeOfMob(context, x, y, z, mob);
            }
        };
        evaluator.setCanPassDoors(actor.getNavigation().getNodeEvaluator().canPassDoors());
        evaluator.setCanOpenDoors(actor.getNavigation().getNodeEvaluator().canOpenDoors());
        evaluator.setCanFloat(actor.getNavigation().getNodeEvaluator().canFloat());
        Path detour = new PathFinder(evaluator, MAX_VISITED_NODES).findPath(
                new PathNavigationRegion(level, origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius)),
                actor, Set.of(target), radius, 0, 1.0F);
        return new Query(detour, List.copyOf(blockers.values()), passage);
    }

    private static boolean following(Mob actor, LivingEntity other, Path path) {
        if (path == null || path.isDone()) return false;
        for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
            var direction = path.getEntityPosAtNode(actor, index).subtract(actor.position());
            // A newly created native path can begin exactly under this body.
            // Its zero-length first node is not a heading or a stationary intent.
            if (direction.horizontalDistanceSqr() <= actor.getBbWidth() * actor.getBbWidth() / 4) continue;
            return FrontierV3PedestrianFollowing.coFlow(actor.position(), direction, other.position(), other.getDeltaMovement());
        }
        return false;
    }

    /** Local following preserves the native path; only a real stationary/crossing blocker replans it. */
    static double followingPace(ServerLevel level, Mob actor, Path path) {
        if (path == null || path.isDone()) return 1;
        double result = 1;
        int end = Math.min(path.getNodeCount(), path.getNextNodeIndex() + 2);
        for (int index = path.getNextNodeIndex(); index < end; index++) {
            for (var other : level.getEntitiesOfClass(LivingEntity.class,
                    nativeNodeBodyAt(actor, path.getNode(index).asBlockPos()),
                    entity -> entity != actor && entity.isAlive() && !entity.isSpectator())) {
                if (following(actor, other, path)) result = Math.min(result,
                        FrontierV3PedestrianFollowing.paceFraction(actor.position().distanceTo(other.position()),
                                actor.getBbWidth(), other.getBbWidth()));
            }
        }
        return result;
    }

    private static AABB stationBodyAt(Mob actor, BlockPos feet) {
        return actor.getBoundingBox().move(feet.getX() + 0.5D - actor.getX(),
                feet.getY() - actor.getY(), feet.getZ() + 0.5D - actor.getZ());
    }

    /** Matches Path#getEntityPosAtNode; station centring is a separate final operation. */
    private static AABB nativeNodeBodyAt(Mob actor, BlockPos feet) {
        double offset = (int) (actor.getBbWidth() + 1.0F) * 0.5D;
        return actor.getBoundingBox().move(feet.getX() + offset - actor.getX(),
                feet.getY() - actor.getY(), feet.getZ() + offset - actor.getZ());
    }
}
