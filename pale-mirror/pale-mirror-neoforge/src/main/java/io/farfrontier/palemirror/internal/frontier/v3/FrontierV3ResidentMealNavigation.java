package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** HOT presentation follows the same known route as the retained COLD meal owner. */
final class FrontierV3ResidentMealNavigation {
    private record Route(long leaseRevision, long mealStart, List<SurfaceAnchor> waypoints) { }

    private static final Map<Mob, Route> ROUTES = new WeakHashMap<>();
    private static final Map<Mob, String> BLOCKED = new WeakHashMap<>();
    private static final int LOCAL_LEG = 4;

    private FrontierV3ResidentMealNavigation() { }

    static void pursue(ServerLevel level, FrontierWorldState state, Mob body,
                       AmbientActorLease lease, ResidentMeal meal) {
        if (meal == null || meal.phase() != ResidentMeal.Phase.MOVE) {
            ROUTES.remove(body);
            BLOCKED.remove(body);
            FrontierV3GoalNavigation.stop(body);
            return;
        }
        Route route = ROUTES.get(body);
        if (route == null || route.leaseRevision() != lease.revision() || route.mealStart() != meal.startedAtTick()) {
            try {
                route = new Route(lease.revision(), meal.startedAtTick(),
                        ResidentMealKnownNavigation.path(state, meal));
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                blocked(body, meal, "known_route:" + unavailable.getMessage());
                FrontierV3GoalNavigation.stop(body);
                return;
            }
        }
        ROUTES.put(body, route);
        if (FrontierV3SemanticMovement.arrived(level, body, route.waypoints().getLast())) {
            BLOCKED.remove(body);
            FrontierV3GoalNavigation.stop(body);
            return;
        }
        int nearest = nearestWaypoint(body, route.waypoints());
        int targetIndex = Math.min(route.waypoints().size() - 1,
                (nearest / LOCAL_LEG + 1) * LOCAL_LEG);
        SurfaceAnchor waypoint = route.waypoints().get(targetIndex);
        if (!level.hasChunkAt(new net.minecraft.core.BlockPos(
                waypoint.x(), waypoint.y() + 1, waypoint.z()))) {
            blocked(body, meal, "physical_waypoint:" + targetIndex + ":" + waypoint.support());
            FrontierV3GoalNavigation.stop(body);
            return;
        }
        LocalNavigationEnvelope envelope;
        try {
            envelope = LocalNavigationEnvelope.localLeg(route.waypoints().subList(
                    Math.max(0, targetIndex - LOCAL_LEG), targetIndex + 1), waypoint);
        } catch (IllegalArgumentException unavailable) {
            blocked(body, meal, "local_leg:" + unavailable.getMessage());
            FrontierV3GoalNavigation.stop(body);
            return;
        }
        MovementOrder order = new MovementOrder(meal.residentId(), meal.residentId(),
                FrontierWireTags.tag(meal.phase()), targetIndex + 1L, List.of(waypoint),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        FrontierV3GoalNavigation.Result result = FrontierV3GoalNavigation.pursue(level, body,
                new FrontierV3GoalNavigation.Goal(order, envelope));
        if (result.status() == FrontierV3GoalNavigation.Status.BLOCKED)
            blocked(body, meal, "minecraft_path:" + result.reason());
        else BLOCKED.remove(body);
    }

    private static int nearestWaypoint(Mob body, List<SurfaceAnchor> route) {
        net.minecraft.core.BlockPos observed = body.getOnPos();
        int nearest = 0;
        long distance = Long.MAX_VALUE;
        for (int index = 0; index < route.size(); index++) {
            SurfaceAnchor point = route.get(index);
            long candidate = Math.abs((long) point.x() - observed.getX())
                    + Math.abs((long) point.y() - observed.getY())
                    + Math.abs((long) point.z() - observed.getZ());
            if (candidate < distance) { nearest = index; distance = candidate; }
        }
        return nearest;
    }

    private static void blocked(Mob body, ResidentMeal meal, String reason) {
        if (!reason.equals(BLOCKED.put(body, reason)))
            PaleMirrorMod.LOGGER.warn("Resident meal route waits resident={} phase={} reason={}",
                    meal.residentId().value(), meal.phase(), reason);
    }
}
