package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Meal policy supplies a final service goal and known-route hint, never a physical corridor. */
final class FrontierV3ResidentMealNavigation {
    private record Route(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId actuation,
                         long leaseRevision, long mealStart, ResidentMeal.Phase phase, boolean admitted,
                         List<SurfaceAnchor> waypoints, long plannedAt) { }

    private static final Map<Mob, Route> ROUTES = new WeakHashMap<>();
    private static final Map<Mob, String> BLOCKED = new WeakHashMap<>();

    private FrontierV3ResidentMealNavigation() { }

    static void pursue(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                       FrontierWorldState state, Mob body,
                       AmbientActorLease lease, ResidentMeal meal) {
        if (meal == null) {
            ROUTES.remove(body);
            BLOCKED.remove(body);
            return; // Its retired command is quiesced at the common entity boundary, never by a new owner.
        }
        var actuation = FrontierV3AmbientActuation.capture(state, runtime, body, lease, meal.executionId()).orElse(null);
        if (actuation == null || !actuation.current(body)) return;
        if (meal.phase() != ResidentMeal.Phase.MOVE
                && !meal.movesToClearance()) {
            ROUTES.remove(body);
            BLOCKED.remove(body);
            FrontierV3GoalNavigation.stop(body, actuation);
            return;
        }
        if (meal.phase() == ResidentMeal.Phase.CLEAR_ACCESS) {
            ROUTES.remove(body);
            var result = FrontierV3ServiceClearanceNavigation.pursue(level, body, state, meal.settlementId(),
                    meal.depotId(), meal.residentId(), meal.residentId(), FrontierWireTags.tag(meal.phase()),
                    meal.startedAtTick() + 1L, actuation);
            if (result.status() == FrontierV3GoalNavigation.Status.BLOCKED)
                blocked(body, meal, "service_exit:" + result.reason());
            else BLOCKED.remove(body);
            return;
        }
        Route route = ROUTES.get(body);
        boolean admitted = ResidentMealServiceAccess.available(state, meal.depotId(), meal.residentId());
        if (route == null || !route.actuation().equals(actuation.id()) || route.leaseRevision() != lease.revision()
                || route.mealStart() != meal.startedAtTick() || route.phase() != meal.phase()
                || route.admitted() != admitted
                || BLOCKED.containsKey(body) && level.getGameTime() - route.plannedAt()
                        >= FrontierV3MinecraftGoalNavigation.retryIntervalTicks()
                || meal.phase() == ResidentMeal.Phase.MOVE
                    && !route.waypoints().getLast().equals(ResidentMealProcess.serviceSurface(state, meal))
                    && (!ResidentMealKnownNavigation.waitingStationAvailable(state, meal, route.waypoints().getLast())
                        || !available(level, body, route.waypoints().getLast()))
                || completedWaitingLegRequiresReplan(route.waypoints(),
                    FrontierV3SurfaceObservation.observedBody(body).supportingSurface(),
                    ResidentMealProcess.serviceSurface(state, meal), admitted, meal.phase())) {
            try {
                route = new Route(actuation.id(), lease.revision(), meal.startedAtTick(), meal.phase(), admitted,
                        meal.phase() == ResidentMeal.Phase.MOVE
                                ? approach(level, state, body, meal)
                                : ResidentMealKnownNavigation.clearancePathFrom(state, meal,
                                    FrontierV3SurfaceObservation.observedBody(body).supportingSurface()), level.getGameTime());
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING)
                    blocked(body, meal, "known_route:" + unavailable.status() + ":" + unavailable.getMessage());
                FrontierV3GoalNavigation.stop(body, actuation);
                return;
            }
        }
        ROUTES.put(body, route);
        if (FrontierV3SemanticMovement.arrived(level, body, route.waypoints().getLast())) {
            BLOCKED.remove(body);
            FrontierV3GoalNavigation.stop(body, actuation);
            return;
        }
        MovementOrder order = new MovementOrder(meal.residentId(), meal.residentId(),
                FrontierWireTags.tag(meal.phase()), meal.startedAtTick() + 1L, List.of(route.waypoints().getLast()),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        FrontierV3GoalNavigation.Result result = FrontierV3GoalNavigation.pursue(level, body,
                FrontierV3GoalNavigation.Goal.routed(order, route.waypoints(), state.bootstrap().bounds()), actuation);
        if (result.status() == FrontierV3GoalNavigation.Status.BLOCKED)
            blocked(body, meal, "minecraft_path:" + result.reason());
        else BLOCKED.remove(body);
    }

    /** A HOT route to a side pocket is only an approach, never a completed meal trip. */
    static boolean completedWaitingLegRequiresReplan(List<SurfaceAnchor> waypoints, SurfaceAnchor observed,
                                                       SurfaceAnchor service, boolean admitted,
                                                       ResidentMeal.Phase phase) {
        return phase == ResidentMeal.Phase.MOVE && admitted && !waypoints.isEmpty()
                && !waypoints.getLast().equals(service) && waypoints.getLast().equals(observed);
    }

    private static void blocked(Mob body, ResidentMeal meal, String reason) {
        if (!reason.equals(BLOCKED.put(body, reason)))
            PaleMirrorMod.LOGGER.warn("Resident meal route waits resident={} phase={} reason={}",
                    meal.residentId().value(), meal.phase(), reason);
    }

    private static boolean available(ServerLevel level, Mob body, SurfaceAnchor station) {
        // An unloaded destination is a known intent, not a physical clearance observation.
        // The shared provider checks each naturally loaded local leg; never force-load the endpoint.
        return !level.hasChunkAt(new net.minecraft.core.BlockPos(station.x(), station.y(), station.z()))
                || FrontierV3SemanticMovement.targetIsNavigable(level, body, station);
    }

    private static List<SurfaceAnchor> approach(ServerLevel level, FrontierWorldState state, Mob body, ResidentMeal meal) {
        var start = FrontierV3SurfaceObservation.observedBody(body).supportingSurface();
        return ResidentMealKnownNavigation.pathFrom(state, meal, start, station -> available(level, body, station));
    }
}
