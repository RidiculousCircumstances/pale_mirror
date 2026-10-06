package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ephemeral COLD route to a resident's declared depot service station. */
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
            return KnownServiceExitNavigation.clearancePathFrom(state, meal.settlementId(), meal.depotId(),
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
                route = KnownServiceExitNavigation.pathFrom(state, meal.settlementId(), meal.depotId(), order, start);
                break;
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
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
        Settlement settlement = state.bootstrap().settlements().stream()
                .filter(value -> value.id().equals(meal.settlementId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("meal navigation has no retained settlement"));
        SettlementStructure depot = settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("meal navigation has no retained depot"));
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        if (!meal.depotId().equals(FrontierWorldState.depotId(settlement.id())))
            throw new IllegalArgumentException("meal navigation has a foreign depot identity");
        boolean admitted = ResidentMealServiceAccess.available(state, meal.depotId(), meal.residentId());
        if (admitted && start.equals(port.serviceSurface())) return List.of(start);
        if (admitted && start.equals(port.exteriorApproach()))
            return serviceLeg(state, meal, depot, port.exteriorApproach());

        List<SurfaceAnchor> prefix = workshopExit(settlement, start);
        SurfaceAnchor outdoorStart = prefix.getLast();
        List<KnownPedestrianRouteKnowledge.Passage> passages = new ArrayList<>();
        passages.add(new KnownPedestrianRouteKnowledge.Passage(depot,
                KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS));
        if (prefix.size() > 1) {
            SettlementStructure workshop = settlement.structures().stream()
                    .filter(structure -> structure.kind() == StructureKind.WORKSHOP)
                    .filter(structure -> SettlementWorkshopServicePort.forWorkshop(structure)
                            .exteriorApproach().equals(outdoorStart))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("meal start has no declared workshop exit"));
            passages.add(new KnownPedestrianRouteKnowledge.Passage(workshop,
                    KnownPedestrianRouteKnowledge.Passage.Reach.EXTERIOR));
        }
        KnownPedestrianRouteKnowledge routeKnowledge = KnownPedestrianRouteKnowledge.forSettlement(
                state, meal.settlementId(), passages);
        // A distant applicant is not an occupant and does not own the service
        // turn. Route it to a side pocket first, then reconsider the short
        // entrance from there. A legitimate pocket can be farther than three
        // blocks from the exterior station on irregular terrain.
        boolean directService = admitted && (Math.abs(start.x() - port.exteriorApproach().x())
                + Math.abs(start.z() - port.exteriorApproach().z()) <= 3
                || SettlementServiceAccessPoints.forDepot(state, settlement, depot).waitingSurfaces().contains(start));
        List<SurfaceAnchor> destinations = directService ? List.of(port.exteriorApproach())
                : waitingSurfaces(state, meal, port, routeKnowledge).stream()
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
                    List<SurfaceAnchor> finalLeg = serviceLeg(state, meal, depot, port.exteriorApproach());
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
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), meal.settlementId());
        var passages = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT
                || value.kind() == StructureKind.WORKSHOP).map(value -> new KnownPedestrianRouteKnowledge.Passage(value,
                    value.kind() == StructureKind.WORKSHOP ? KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS
                            : KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)).toList();
        KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), passages).requireRoute(route);
        var port = SettlementServiceAccessPoints.depotPort(state, meal.settlementId());
        var end = route.getLast();
        if (meal.phase() == ResidentMeal.Phase.MOVE) {
            if (end.equals(port.serviceSurface())) {
                if (!ResidentMealServiceAccess.available(state, meal.depotId(), meal.residentId()))
                    throw new IllegalArgumentException("accepted meal entrance lost its access turn");
            } else if (!waitingStationAvailable(state, meal, end) || !port.accessBoundary().allowsWaitingRoute(route))
                throw new IllegalArgumentException("accepted meal approach is not a current waiting destination");
        } else if (!port.accessBoundary().cleared(end.standingBody()))
            throw new IllegalArgumentException("accepted meal clearance does not clear its access boundary");
    }

    /** Side pockets keep simultaneous approaches off the single-file exit route. */
    public static boolean atWaitingPocket(FrontierWorldState state, ResidentMeal meal) {
        ActorLocation actor = state.actorLocations().get(meal.residentId());
        if (actor == null) return false;
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), meal.settlementId());
        SettlementStructure depot = settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        return SettlementServiceAccessPoints.forDepot(state, settlement, depot).waitingSurfaces()
                .contains(actor.supportingSurface());
    }

    private static List<SurfaceAnchor> waitingSurfaces(FrontierWorldState state, ResidentMeal meal,
                                                       SettlementDepotServicePort port,
                                                       KnownPedestrianRouteKnowledge routeKnowledge) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), meal.settlementId());
        int ordinal = 0;
        for (int index = 0; index < settlement.residents().size(); index++) {
            if (settlement.residents().get(index).id().equals(meal.residentId())) {
                ordinal = index;
                break;
            }
        }
        SettlementStructure depot = settlement.structures().stream().filter(value -> value.id().equals(port.depotId())).findFirst().orElseThrow();
        var excluded = ServiceDestinationClaims.excludedFor(state, meal.residentId());
        List<SurfaceAnchor> candidates = SettlementServiceAccessPoints.forDepot(state, settlement, depot).waitingSurfaces()
                .stream().filter(surface -> !excluded.contains(surface)).toList();
        if (candidates.isEmpty()) return List.of();
        List<SurfaceAnchor> preferred = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++)
            preferred.add(candidates.get((ordinal + index) % candidates.size()));
        return List.copyOf(preferred);
    }

    /** A retained approach cannot park on another owner's current or reserved exit. */
    public static boolean waitingStationAvailable(FrontierWorldState state, ResidentMeal meal, SurfaceAnchor surface) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), meal.settlementId());
        SettlementStructure depot = settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        var point = SettlementServiceAccessPoints.forDepot(state, settlement, depot);
        // COLD stops its admitted entrance leg just before crossing the shared
        // boundary. That transient approach is not a waiting/parking claim.
        if ((point.egressSurfaces().contains(surface) || point.boundary().occupied(surface.standingBody()))
                && ResidentMealServiceAccess.available(state, meal.depotId(), meal.residentId()))
            return !ServiceDestinationClaims.forImmediateExit(state, meal.residentId()).contains(surface);
        return point.waitingSurfaces().contains(surface)
                && !ServiceDestinationClaims.excludedFor(state, meal.residentId()).contains(surface);
    }

    private static List<SurfaceAnchor> workshopExit(Settlement settlement, SurfaceAnchor start) {
        for (SettlementStructure structure : settlement.structures()) {
            if (structure.kind() != StructureKind.WORKSHOP) continue;
            SettlementWorkshopServicePort port = SettlementWorkshopServicePort.forWorkshop(structure);
            List<SurfaceAnchor> exit = List.of(port.workStation(), port.inputStation(),
                    port.interiorSurface(), port.throatSurface(), port.approachSurface(), port.exteriorApproach());
            int index = exit.indexOf(start);
            if (index >= 0) return exit.subList(index, exit.size());
        }
        return List.of(start);
    }

    private static List<SurfaceAnchor> serviceLeg(FrontierWorldState state, ResidentMeal meal,
                                                   SettlementStructure depot, SurfaceAnchor start) {
        MovementOrder order = new MovementOrder(meal.residentId(), meal.residentId(),
                FrontierWireTags.tag(meal.phase()), 1L,
                List.of(SettlementDepotServicePort.forDepot(depot).serviceSurface()),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        return KnownPedestrianRouteKnowledge.path(state, meal.settlementId(), start, order,
                List.of(new KnownPedestrianRouteKnowledge.Passage(depot,
                        KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
    }
}
