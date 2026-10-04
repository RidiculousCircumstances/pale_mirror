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
                         List<SurfaceAnchor> waypoints) { }

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
            var stations = KnownServiceExitNavigation.exitStations(state, meal.settlementId(), meal.depotId(),
                    meal.residentId(), FrontierV3SurfaceObservation.observedBody(body).supportingSurface()).stream()
                    .filter(station -> available(level, body, station)).toList();
            if (stations.isEmpty()) {
                blocked(body, meal, "service_exit:no-available-supported-exit");
                FrontierV3GoalNavigation.stop(body, actuation);
                return;
            }
            String lastReason = "no-reachable-exit";
            for (int offset = 0; offset < stations.size(); offset += MovementOrder.MAX_LEGAL_STATIONS) {
                var batch = stations.subList(offset, Math.min(stations.size(), offset + MovementOrder.MAX_LEGAL_STATIONS));
                MovementOrder order = new MovementOrder(meal.residentId(), meal.residentId(),
                        FrontierWireTags.tag(meal.phase()), meal.startedAtTick() + 1L, batch,
                        TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
                var result = FrontierV3GoalNavigation.pursue(level, body,
                        FrontierV3GoalNavigation.Goal.routed(order, List.of(), state.bootstrap().bounds()), actuation);
                if (result.status() != FrontierV3GoalNavigation.Status.BLOCKED) {
                    BLOCKED.remove(body);
                    return;
                }
                lastReason = result.reason();
            }
            blocked(body, meal, "service_exit:" + lastReason);
            return;
        }
        Route route = ROUTES.get(body);
        boolean admitted = ServiceAccessCoordinator.depotAvailableForMeal(state, meal.depotId(), meal.residentId());
        if (route == null || !route.actuation().equals(actuation.id()) || route.leaseRevision() != lease.revision()
                || route.mealStart() != meal.startedAtTick() || route.phase() != meal.phase()
                || route.admitted() != admitted
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
                                ? ResidentMealKnownNavigation.pathFrom(state, meal,
                                    FrontierV3SurfaceObservation.observedBody(body).supportingSurface(),
                                    station -> available(level, body, station))
                                : ResidentMealKnownNavigation.clearancePathFrom(state, meal,
                                    FrontierV3SurfaceObservation.observedBody(body).supportingSurface()));
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                blocked(body, meal, "known_route:" + unavailable.getMessage());
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
        return level.hasChunkAt(new net.minecraft.core.BlockPos(station.x(), station.y(), station.z()))
                && FrontierV3SemanticMovement.targetIsNavigable(level, body, station);
    }
}
