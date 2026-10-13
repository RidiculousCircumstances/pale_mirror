package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianPathComposition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Cooperative known path to the current bakery handoff station; no private synchronous runtime planner. */
public final class BakeryKnownNavigation {
    private BakeryKnownNavigation() { }

    public static List<SurfaceAnchor> path(FrontierWorldState state, ProductionJob job) {
        Objects.requireNonNull(state, "bakery navigation state");
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("bakery movement has no living actor");
        return pathFrom(state, job, actor.supportingSurface());
    }

    /** HOT may start from the scene's last durable body rather than the dormant COLD body. */
    public static List<SurfaceAnchor> pathFrom(FrontierWorldState state, ProductionJob job, SurfaceAnchor start) {
        return pathFrom(state, job, start, false);
    }

    public static List<SurfaceAnchor> plannedPath(FrontierWorldState state, ProductionJob job) {
        var actor = Objects.requireNonNull(state.actorLocations().get(job.workerId()), "bakery actor");
        if (actor.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("bakery actor is not alive");
        return pathFrom(state, job, actor.supportingSurface(), false);
    }

    public static List<SurfaceAnchor> retainedPath(FrontierWorldState state, ProductionJob job) {
        return pathFrom(state, job, state.actorLocations().get(job.workerId()).supportingSurface(), true);
    }

    private static List<SurfaceAnchor> pathFrom(FrontierWorldState state, ProductionJob job, SurfaceAnchor start,
            boolean retainedOnly) {
        Objects.requireNonNull(state, "bakery navigation state");
        Objects.requireNonNull(start, "bakery navigation start");
        BakeryWorkState work = job.bakeryWork().orElseThrow(() -> new IllegalArgumentException("job has no bakery movement goal"));
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId());
        SettlementStructure facility = settlement.structures().stream().filter(value -> value.id().equals(job.facilityId()))
                .findFirst().orElseThrow();
        var depotPort = BakeryStorage.access(state, job);
        var serviceStation = depotPort.interactionStation(state);
        var exterior = ShipmentEndpointComposition.exteriorApproach(state, depotPort.endpoint());
        SettlementWorkshopServicePort workshopPort = SettlementWorkshopServicePort.forWorkshop(facility);
        boolean clearing = work.phase() == BakeryWorkState.Phase.DELIVERED;
        boolean toDepot = work.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY;
        if (clearing) return retainedOnly
                ? KnownServiceExitNavigation.retainedClearancePathFrom(state, job.settlementId(),
                    BakeryStorage.destination(job), job.id(), job.workerId(), work.phase().wireTag(), 1L, start)
                : KnownServiceExitNavigation.plannedClearancePathFrom(state, job.settlementId(),
                    BakeryStorage.destination(job), job.id(), job.workerId(), work.phase().wireTag(), 1L, start);
        SurfaceAnchor goal = BakeryWorkGoal.current(state, job).station();
        if (start.equals(goal)) return List.of(start);
        boolean atWorkshop = start.equals(workshopPort.workStation()) || start.equals(workshopPort.inputStation())
                || start.equals(workshopPort.interiorSurface()) || start.equals(workshopPort.throatSurface())
                || start.equals(workshopPort.approachSurface());
        if (!toDepot && !clearing && atWorkshop) return workshopIngress(workshopPort, start);
        List<SurfaceAnchor> prefix = atWorkshop ? workshopExit(workshopPort, start)
                : start.equals(serviceStation) ? List.of(start, exterior) : List.of(start);
        SurfaceAnchor outdoorStart = prefix.getLast();
        SurfaceAnchor outdoorGoal = toDepot ? exterior : workshopPort.exteriorApproach();
        MovementOrder outdoor = new MovementOrder(job.id(), job.workerId(), work.phase().wireTag(), 1,
                List.of(outdoorGoal), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        var passages = new ArrayList<>(ShipmentEndpointComposition.passages(state, depotPort.endpoint()));
        passages.add(new KnownPedestrianRouteKnowledge.SettlementPassage(job.settlementId(),
                new KnownPedestrianRouteKnowledge.Passage(facility, KnownPedestrianRouteKnowledge.Passage.Reach.EXTERIOR)));
        KnownPedestrianRouteKnowledge routeKnowledge = KnownPedestrianRouteKnowledge.forJourney(state, passages).forActor(state, job.workerId());
        List<SurfaceAnchor> route = retainedOnly ? routeKnowledge.retainedPath(outdoorStart, outdoor)
                : routeKnowledge.plannedPath(outdoorStart, outdoor);
        List<SurfaceAnchor> result = new ArrayList<>(prefix);
        result.addAll(route.subList(1, route.size()));
        if (toDepot) {
            MovementOrder serviceOrder = new MovementOrder(job.id(), job.workerId(), work.phase().wireTag(), 1L,
                    List.of(serviceStation), TraversalCapability.PEDESTRIAN,
                    MovementOrder.ArrivalPolicy.EXACT_STATION);
            List<SurfaceAnchor> serviceLeg = retainedOnly ? routeKnowledge.retainedPath(exterior, serviceOrder)
                    : routeKnowledge.plannedPath(exterior, serviceOrder);
            result.addAll(serviceLeg.subList(1, serviceLeg.size()));
        } else if (!clearing) {
            result.addAll(List.of(workshopPort.approachSurface(), workshopPort.throatSurface(),
                    workshopPort.interiorSurface(), workshopPort.inputStation(), workshopPort.workStation()));
        }
        return PedestrianPathComposition.withoutLoops(result);
    }

    /** Bounded current-fact validation; replay does not calculate or rank routes. */
    public static void requireAcceptedDestination(FrontierWorldState state, ProductionJob job,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt> receipt) {
        if (receipt.isPresent()) requireAcceptedRoute(state, job, receipt.orElseThrow());
        else if (!state.actorLocations().get(job.workerId()).supportingSurface().equals(BakeryWorkGoal.current(state, job).station()))
            throw new IllegalArgumentException("bakery admission lacks its route or observed destination");
    }

    /** Bounded current-fact validation; replay does not calculate or rank routes. */
    public static void requireAcceptedRoute(FrontierWorldState state, ProductionJob job,
            io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt receipt) {
        var route = receipt.route();
        if (!route.getFirst().equals(state.actorLocations().get(job.workerId()).supportingSurface()))
            throw new IllegalArgumentException("bakery route has a stale departure");
        var work = job.bakeryWork().orElseThrow();
        if (work.phase() == BakeryWorkState.Phase.DELIVERED) {
            var point = ServiceBoundaryComposition.declaration(state, BakeryStorage.destination(job));
            if (!point.boundary().cleared(route.getLast().standingBody())
                    || ServiceDestinationClaims.forImmediateExit(state, job.workerId()).contains(route.getLast()))
                throw new IllegalArgumentException("bakery route does not clear an available service boundary");
            ServiceBoundaryComposition.knowledge(state, point.identity()).forActor(state, job.workerId()).requireRoute(route);
            return;
        }
        if (!route.getLast().equals(BakeryWorkGoal.current(state, job).station()))
            throw new IllegalArgumentException("bakery route has a foreign phase goal");
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId());
        var facility = home.structures().stream().filter(value -> value.id().equals(job.facilityId())).findFirst().orElseThrow();
        var passages = new ArrayList<>(ShipmentEndpointComposition.passages(state, BakeryStorage.access(state, job).endpoint()));
        passages.add(new KnownPedestrianRouteKnowledge.SettlementPassage(job.settlementId(),
                new KnownPedestrianRouteKnowledge.Passage(facility, KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS)));
        KnownPedestrianRouteKnowledge.forJourney(state, passages).forActor(state, job.workerId()).requireRoute(route);
    }

    private static List<SurfaceAnchor> workshopExit(SettlementWorkshopServicePort port, SurfaceAnchor start) {
        List<SurfaceAnchor> exit = List.of(port.workStation(), port.inputStation(), port.interiorSurface(),
                port.throatSurface(), port.approachSurface(), port.exteriorApproach());
        return exit.subList(exit.indexOf(start), exit.size());
    }

    private static List<SurfaceAnchor> workshopIngress(SettlementWorkshopServicePort port, SurfaceAnchor start) {
        List<SurfaceAnchor> ingress = List.of(port.approachSurface(), port.throatSurface(), port.interiorSurface(),
                port.inputStation(), port.workStation());
        return ingress.subList(ingress.indexOf(start), ingress.size());
    }
}
