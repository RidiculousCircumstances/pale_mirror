package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ephemeral COLD route to the meal's retained storage capability. */
public final class ResidentMealKnownNavigation {
    private ResidentMealKnownNavigation() { }

    public static List<SurfaceAnchor> path(FrontierWorldState state, ResidentMeal meal) {
        Objects.requireNonNull(state, "meal navigation state");
        Objects.requireNonNull(meal, "meal navigation owner");
        ActorLocation actor = state.actorLocations().get(meal.residentId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("meal navigation has no living resident body");
        return pathFrom(state, meal, actor.supportingSurface());
    }

    /** The same retained body walks away from the shared socket before the next user enters. */
    public static List<SurfaceAnchor> returnPath(FrontierWorldState state, ResidentMeal meal) {
        return clearancePathFrom(state, meal, state.actorLocations().get(meal.residentId()).supportingSurface());
    }

    public static List<SurfaceAnchor> clearancePathFrom(FrontierWorldState state, ResidentMeal meal, SurfaceAnchor start) {
        if (meal.phase() == ResidentMeal.Phase.CLEAR_ACCESS)
            return KnownServiceExitNavigation.plannedClearancePathFrom(state, meal.settlementId(), meal.depotId(),
                    meal.residentId(), meal.residentId(), FrontierWireTags.tag(meal.phase()), 1L, start);
        List<SurfaceAnchor> destinations = List.of(meal.clearingSurface());
        List<SurfaceAnchor> route = null;
        for (int offset = 0; offset < destinations.size(); offset += MovementOrder.MAX_LEGAL_STATIONS) {
            List<SurfaceAnchor> batch = destinations.subList(offset,
                    Math.min(destinations.size(), offset + MovementOrder.MAX_LEGAL_STATIONS));
            MovementOrder order = new MovementOrder(meal.residentId(), meal.residentId(),
                    FrontierWireTags.tag(meal.phase()), 1L, batch, TraversalCapability.PEDESTRIAN,
                    MovementOrder.ArrivalPolicy.EXACT_STATION);
            try {
                route = KnownServiceExitNavigation.plannedPathFrom(state, meal.settlementId(), meal.depotId(), order, start);
                break;
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)
                    throw unavailable;
                // Exhaust this bounded region, not just one preferred exit.
            }
        }
        if (route == null) throw new KnownPedestrianNavigation.RouteUnavailable("no reachable service exit");
        return route;
    }

    /** A HOT release can resume from its last witnessed body without replaying an old path. */
    public static List<SurfaceAnchor> pathFrom(FrontierWorldState state, ResidentMeal meal, SurfaceAnchor start) {
        return pathFrom(state, meal, start, surface -> true);
    }

    /** HOT supplies read-only physical availability of destinations, never a private terrain graph. */
    public static List<SurfaceAnchor> pathFrom(FrontierWorldState state, ResidentMeal meal, SurfaceAnchor start,
                                              java.util.function.Predicate<SurfaceAnchor> availableWaitingStation) {
        Objects.requireNonNull(state, "meal navigation state");
        Objects.requireNonNull(meal, "meal navigation owner");
        Objects.requireNonNull(start, "meal navigation start");
        var port = ResourceStorageAccess.at(state, meal.depotId());
        if (!port.settlementId().equals(meal.settlementId()))
            throw new IllegalArgumentException("meal navigation has a foreign storage identity");
        var service = port.interactionStation(state);
        var exterior = ShipmentEndpointComposition.exteriorApproach(state, port.endpoint());
        var point = point(state, meal);
        boolean admitted = ResidentMealServiceAccess.available(state, meal.depotId(), meal.residentId());
        if (admitted && start.equals(service)) return List.of(start);
        if (admitted && start.equals(exterior))
            return serviceLeg(state, meal, port, exterior);

        List<SurfaceAnchor> prefix = PedestrianLocalDeparture.exitToPublic(state, meal.residentId(), start);
        SurfaceAnchor outdoorStart = prefix.getLast();
        KnownPedestrianRouteKnowledge routeKnowledge = ShipmentEndpointComposition.knowledge(state, List.of(port.endpoint()));
        // A distant applicant is not an occupant and does not own the service
        // turn. Route it to a side pocket first, then reconsider the short
        // entrance from there. A legitimate pocket can be farther than three
        // blocks from the exterior station on irregular terrain.
        boolean directService = admitted && (Math.abs(start.x() - exterior.x())
                + Math.abs(start.z() - exterior.z()) <= 3 || point.waitingSurfaces().contains(start));
        List<SurfaceAnchor> destinations = directService ? List.of(exterior)
                : waitingSurfaces(state, meal, point).stream()
                    .filter(availableWaitingStation).toList();
        KnownPedestrianNavigation.RouteUnavailable lastUnavailable = null;
        for (SurfaceAnchor destination : destinations) {
            if (start.equals(destination)) return List.of(start);
            MovementOrder outdoor = new MovementOrder(meal.residentId(), meal.residentId(),
                    FrontierWireTags.tag(meal.phase()), 1L, List.of(destination),
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
            try {
                List<SurfaceAnchor> route = routeKnowledge.plannedPath(outdoorStart, outdoor);
                if (!directService && !port.accessBoundary().allowsWaitingRoute(route)) continue;
                List<SurfaceAnchor> result = new ArrayList<>(prefix);
                result.addAll(route.subList(1, route.size()));
                if (directService) {
                    List<SurfaceAnchor> finalLeg = serviceLeg(state, meal, port, exterior);
                    result.addAll(finalLeg.subList(1, finalLeg.size()));
                }
                return io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianPathComposition.withoutLoops(result);
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                lastUnavailable = unavailable;
                if (unavailable.status() == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING)
                    throw unavailable; // Do not fill the shared queue with every waiting point for one resident.
                if (directService) throw unavailable;
            }
        }
        if (lastUnavailable != null) throw new KnownPedestrianNavigation.RouteUnavailable(lastUnavailable.status(),
                "depot approach: " + lastUnavailable.getMessage());
        throw new KnownPedestrianNavigation.RouteUnavailable("no clear depot waiting surface or legal access approach");
    }

    /** Accept a current semantic leg without repeating search during WAL replay. */
    public static void requireMovementRoute(FrontierWorldState state, ResidentMeal meal, List<SurfaceAnchor> route) {
        var port = ResourceStorageAccess.at(state, meal.depotId());
        PedestrianLocalDeparture.departure(state, route.getFirst(),
                ShipmentEndpointComposition.passages(state, port.endpoint())).requireRoute(route);
        var end = route.getLast();
        if (meal.phase() == ResidentMeal.Phase.MOVE) {
            if (end.equals(port.interactionStation(state))) {
                if (!ResidentMealServiceAccess.available(state, meal.depotId(), meal.residentId()))
                    throw new IllegalArgumentException("accepted meal entrance lost its access turn");
            } else if (!waitingStationAvailable(state, meal, end)
                    || !port.accessBoundary().allowsWaitingRoute(PedestrianLocalDeparture.publicContinuation(state, route)))
                throw new IllegalArgumentException("accepted meal approach is not a current waiting destination");
        } else if (!port.accessBoundary().cleared(end.standingBody()))
            throw new IllegalArgumentException("accepted meal clearance does not clear its access boundary");
    }

    /** Side pockets keep simultaneous approaches off the single-file exit route. */
    public static boolean atWaitingPocket(FrontierWorldState state, ResidentMeal meal) {
        if (meal.portable()) return false;
        ActorLocation actor = state.actorLocations().get(meal.residentId());
        if (actor == null) return false;
        return point(state, meal).waitingSurfaces()
                .contains(actor.supportingSurface());
    }

    private static List<SurfaceAnchor> waitingSurfaces(FrontierWorldState state, ResidentMeal meal, ServiceAccessPoint point) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), meal.settlementId());
        int ordinal = 0;
        for (int index = 0; index < settlement.residents().size(); index++) {
            if (settlement.residents().get(index).id().equals(meal.residentId())) {
                ordinal = index;
                break;
            }
        }
        var excluded = ServiceDestinationClaims.excludedFor(state, meal.residentId());
        List<SurfaceAnchor> candidates = point.waitingSurfaces()
                .stream().filter(surface -> !excluded.contains(surface)).toList();
        if (candidates.isEmpty()) return List.of();
        List<SurfaceAnchor> preferred = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++)
            preferred.add(candidates.get((ordinal + index) % candidates.size()));
        return List.copyOf(preferred);
    }

    /** A retained approach cannot park on another owner's current or reserved exit. */
    public static boolean waitingStationAvailable(FrontierWorldState state, ResidentMeal meal, SurfaceAnchor surface) {
        var point = point(state, meal);
        // COLD stops its admitted entrance leg just before crossing the shared
        // boundary. That transient approach is not a waiting/parking claim.
        if ((point.egressSurfaces().contains(surface) || point.boundary().occupied(surface.standingBody()))
                && ResidentMealServiceAccess.available(state, meal.depotId(), meal.residentId()))
            return !ServiceDestinationClaims.forImmediateExit(state, meal.residentId()).contains(surface);
        return point.waitingSurfaces().contains(surface)
                && !ServiceDestinationClaims.excludedFor(state, meal.residentId()).contains(surface);
    }


    private static ServiceAccessPoint point(FrontierWorldState state, ResidentMeal meal) {
        return ServiceBoundaryComposition.geometry(state, ServiceBoundaryComposition.declaration(state, meal.depotId()).identity());
    }

    private static List<SurfaceAnchor> serviceLeg(FrontierWorldState state, ResidentMeal meal,
                                                   ResourceStorageAccess port, SurfaceAnchor start) {
        MovementOrder order = new MovementOrder(meal.residentId(), meal.residentId(),
                FrontierWireTags.tag(meal.phase()), 1L,
                List.of(port.interactionStation(state)),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        return ShipmentEndpointComposition.knowledge(state, List.of(port.endpoint())).plannedPath(start, order);
    }
}
