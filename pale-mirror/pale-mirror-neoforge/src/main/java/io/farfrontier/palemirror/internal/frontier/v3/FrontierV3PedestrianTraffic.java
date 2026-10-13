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
    private static final java.util.Map<Mob, NearObservation> NEAR = new java.util.WeakHashMap<>();
    record NearObservation(java.lang.ref.WeakReference<ServerLevel> level, long tick, Path path, int node, net.minecraft.world.phys.Vec3 position,
                           boolean blocked, boolean crossing, double pace) { }
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
        return nearby(level, actor, path).blocked();
    }

    /** One bounded native spatial query per current body/path/node/physical turn.
     * Shared by blockage, courtesy waiting and pace; actual collision stays Minecraft-owned. */
    static NearObservation nearby(ServerLevel level, Mob actor, Path path) {
        if (path == null || path.isDone()) return new NearObservation(new java.lang.ref.WeakReference<>(level), level.getGameTime(), path, -1, actor.position(), false, false, 1);
        var prior = NEAR.get(actor);
        if (prior != null && prior.level().get() == level && prior.tick() == level.getGameTime() && prior.path() == path
                && prior.node() == path.getNextNodeIndex() && prior.position().equals(actor.position())) return prior;
        int end = Math.min(path.getNodeCount(), path.getNextNodeIndex() + 2);
        AABB area = actor.getBoundingBox();
        for (int index = path.getNextNodeIndex(); index < end; index++) area = area.minmax(nativeNodeBodyAt(actor, path.getNode(index).asBlockPos()));
        var peers = level.getEntitiesOfClass(LivingEntity.class, area,
                other -> other != actor && other.isAlive() && !other.isSpectator());
        boolean blocked = false, crossing = false; double pace = 1;
        for (var peer : peers) {
            boolean intersects = false;
            for (int index = path.getNextNodeIndex(); index < end; index++)
                if (nativeNodeBodyAt(actor, path.getNode(index).asBlockPos()).intersects(peer.getBoundingBox())) { intersects = true; break; }
            if (!intersects) continue;
            if (following(actor, peer, path)) pace = Math.min(pace, FrontierV3PedestrianFollowing.paceFraction(
                    actor.position().distanceTo(peer.position()), actor.getBbWidth(), peer.getBbWidth()));
            else {
                blocked = true;
                var direction = heading(actor, path);
                crossing |= FrontierV3PedestrianFollowing.crossing(direction, peer.getDeltaMovement());
            }
        }
        var result = new NearObservation(new java.lang.ref.WeakReference<>(level), level.getGameTime(), path, path.getNextNodeIndex(), actor.position(), blocked, crossing, pace);
        NEAR.put(actor, result); return result;
    }

    static void forget(Mob actor) { NEAR.remove(actor); }

    /** No new path search: only the requester's actual body and remaining accepted native path. */
    static java.util.Optional<Boolean> needsPassage(Mob requester, Path acceptedPath, AABB formerObstruction) {
        return needsPassage(requester.getBoundingBox(), requester.position(), requester.getBbWidth(),
                acceptedPath, formerObstruction);
    }

    static java.util.Optional<Boolean> needsPassage(AABB body, net.minecraft.world.phys.Vec3 position, float width,
                                                   Path path, AABB formerObstruction) {
        if (body.intersects(formerObstruction)) return java.util.Optional.of(true);
        if (path == null) return java.util.Optional.empty();
        if (path.getNodeCount() > FrontierV3PhysicalPathPolicy.MAX_PATH_NODES) return java.util.Optional.empty();
        // The last node remains a centring destination until the exact demand is finished.
        for (int index = Math.min(path.getNextNodeIndex(), Math.max(0, path.getNodeCount() - 1)); index < path.getNodeCount(); index++)
            if (nativeNodeBodyAt(body, position, width, path.getNode(index).asBlockPos()).intersects(formerObstruction))
                return java.util.Optional.of(true);
        return java.util.Optional.of(false);
    }

    static Path createPath(ServerLevel level, Mob actor, BlockPos target, FrontierV3NavigationScope scope) {
        return query(level, actor, target, scope).path();
    }

    static Query query(ServerLevel level, Mob actor, BlockPos target, FrontierV3NavigationScope scope) {
        return query(level, actor, target, scope, java.util.Optional.empty());
    }
    static Query query(ServerLevel level, Mob actor, BlockPos target, FrontierV3NavigationScope scope,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile> profile) {
        Path unconstrained = actor.getNavigation().createPath(target, 0);
        // A rejected unconstrained shortest path does not prove no legal scoped detour.
        // Failed terrain evidence cannot, however, be attributed to other bodies.
        Path ordinary = FrontierV3PhysicalPathPolicy.reject(level, unconstrained, scope).isPresent() || !permitsProfile(unconstrained, profile)
                ? search(level, actor, target, scope, List.of(), profile) : unconstrained;
        if (FrontierV3PhysicalPathPolicy.reject(level, ordinary, scope).isPresent())
            return new Query(ordinary, List.of(), List.of());
        java.util.Map<java.util.UUID, Body> blockers = new java.util.LinkedHashMap<>();
        java.util.List<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> passage = new java.util.ArrayList<>();
        AABB routeArea = actor.getBoundingBox();
        for (int index = ordinary.getNextNodeIndex(); index < ordinary.getNodeCount(); index++)
            routeArea = routeArea.minmax(nativeNodeBodyAt(actor, ordinary.getNode(index).asBlockPos()));
        var routePeers = level.getEntitiesOfClass(LivingEntity.class, routeArea,
                entity -> entity != actor && entity.isAlive() && !entity.isSpectator() && !following(actor, entity, ordinary));
        for (int index = ordinary.getNextNodeIndex(); index < ordinary.getNodeCount(); index++) {
            var feet = ordinary.getNode(index).asBlockPos();
            passage.add(new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(feet.getX(), feet.getY() - 1, feet.getZ()));
            for (var other : routePeers) {
                if (!nativeNodeBodyAt(actor, feet).intersects(other.getBoundingBox())) continue;
                if (blockers.size() >= MAX_BODIES) return new Query(null, List.of(), List.of(), true);
                blockers.putIfAbsent(other.getUUID(), new Body(other.getUUID(), other.getBoundingBox()));
            }
        }
        if (blockers.isEmpty()) return new Query(ordinary, List.of(), passage);
        // Use the same native graph and block/step semantics, adding only ephemeral body
        // clearance. The native region uses getChunkNow: no tickets or unloaded reads.
        int radius = MAX_QUERY_RADIUS;
        BlockPos origin = actor.blockPosition();
        AABB query = new AABB(origin.getX() - radius, origin.getY() - radius, origin.getZ() - radius,
                origin.getX() + radius + 1, origin.getY() + radius + 1, origin.getZ() + radius + 1);
        List<AABB> bodies = level.getEntitiesOfClass(LivingEntity.class, query,
                other -> other != actor && other.isAlive() && !other.isSpectator() && !following(actor, other, ordinary))
                .stream().limit(MAX_BODIES + 1L).map(LivingEntity::getBoundingBox).toList();
        if (bodies.size() > MAX_BODIES) return new Query(null, List.of(), List.of(), true);
        return new Query(search(level, actor, target, scope, bodies, profile), List.copyOf(blockers.values()), passage);
    }

    /** The same native step/door semantics, with hard scope applied during expansion. */
    private static Path search(ServerLevel level, Mob actor, BlockPos target, FrontierV3NavigationScope scope, List<AABB> bodies,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile> profile) {
        int radius = MAX_QUERY_RADIUS;
        BlockPos origin = actor.blockPosition();
        if (Math.abs((long) target.getX() - origin.getX()) > radius
                || Math.abs((long) target.getY() - origin.getY()) > radius
                || Math.abs((long) target.getZ() - origin.getZ()) > radius) return null;
        WalkNodeEvaluator evaluator = new WalkNodeEvaluator() {
            @Override public int getNeighbors(net.minecraft.world.level.pathfinder.Node[] neighbours, net.minecraft.world.level.pathfinder.Node from) {
                int count = super.getNeighbors(neighbours, from), retained = 0;
                for (int index = 0; index < count; index++) {
                    var next = neighbours[index];
                    if (profile.isEmpty() || profile.orElseThrow().permitsGrade(from.y, next.y)) neighbours[retained++] = next;
                }
                return retained;
            }
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
        return new PathFinder(evaluator, MAX_VISITED_NODES).findPath(
                new PathNavigationRegion(level, origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius)),
                actor, Set.of(target), radius, 0, 1.0F);
    }
    static boolean permitsProfile(Path path, java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile> profile) {
        if (path == null) return false;
        if (profile.isEmpty()) return true; // Explicit unmodeled physical query, not a resident-profile fallback.
        for (int index = 1; index < path.getNodeCount(); index++)
            if (!profile.orElseThrow().permitsGrade(path.getNode(index - 1).y, path.getNode(index).y)) return false;
        return true;
    }

    private static boolean following(Mob actor, LivingEntity other, Path path) {
        if (path == null || path.isDone()) return false;
        return FrontierV3PedestrianFollowing.coFlow(actor.position(), heading(actor, path), other.position(), other.getDeltaMovement());
    }

    private static net.minecraft.world.phys.Vec3 heading(Mob actor, Path path) {
        for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
            var direction = path.getEntityPosAtNode(actor, index).subtract(actor.position());
            // A newly created native path can begin exactly under this body.
            // Its zero-length first node is not a heading or a stationary intent.
            if (direction.horizontalDistanceSqr() <= actor.getBbWidth() * actor.getBbWidth() / 4) continue;
            return direction;
        }
        return net.minecraft.world.phys.Vec3.ZERO;
    }

    /** Local following preserves the native path; only a real stationary/crossing blocker replans it. */
    static double followingPace(ServerLevel level, Mob actor, Path path) {
        return nearby(level, actor, path).pace();
    }

    private static AABB stationBodyAt(Mob actor, BlockPos feet) {
        return actor.getBoundingBox().move(feet.getX() + 0.5D - actor.getX(),
                feet.getY() - actor.getY(), feet.getZ() + 0.5D - actor.getZ());
    }

    /** Matches Path#getEntityPosAtNode; station centring is a separate final operation. */
    private static AABB nativeNodeBodyAt(Mob actor, BlockPos feet) {
        return nativeNodeBodyAt(actor.getBoundingBox(), actor.position(), actor.getBbWidth(), feet);
    }

    private static AABB nativeNodeBodyAt(AABB bounds, net.minecraft.world.phys.Vec3 position, float width, BlockPos feet) {
        double offset = (int) (width + 1.0F) * 0.5D;
        return bounds.move(feet.getX() + offset - position.x(), feet.getY() - position.y(), feet.getZ() + offset - position.z());
    }
}
