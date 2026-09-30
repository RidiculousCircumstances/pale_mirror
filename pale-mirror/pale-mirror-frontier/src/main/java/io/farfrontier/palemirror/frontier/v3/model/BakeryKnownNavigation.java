package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ephemeral COLD path to the current bakery handoff station; no route cursor is persisted. */
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
        Objects.requireNonNull(state, "bakery navigation state");
        Objects.requireNonNull(start, "bakery navigation start");
        BakeryWorkState work = job.bakeryWork().orElseThrow(() -> new IllegalArgumentException("job has no bakery movement goal"));
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId());
        SettlementStructure depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow();
        SettlementStructure facility = settlement.structures().stream().filter(value -> value.id().equals(job.facilityId()))
                .findFirst().orElseThrow();
        SettlementDepotServicePort depotPort = SettlementDepotServicePort.forDepot(depot);
        SettlementWorkshopServicePort workshopPort = SettlementWorkshopServicePort.forWorkshop(facility);
        boolean clearing = work.phase() == BakeryWorkState.Phase.DELIVERED;
        boolean toDepot = work.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY;
        SurfaceAnchor goal = BakeryWorkGoal.current(state, job).station();
        if (start.equals(goal)) return List.of(start);
        boolean atWorkshop = start.equals(workshopPort.workStation()) || start.equals(workshopPort.inputStation())
                || start.equals(workshopPort.interiorSurface()) || start.equals(workshopPort.throatSurface())
                || start.equals(workshopPort.approachSurface());
        if (!toDepot && !clearing && atWorkshop) return workshopIngress(workshopPort, start);
        List<SurfaceAnchor> prefix = atWorkshop ? workshopExit(workshopPort, start)
                : start.equals(depotPort.serviceSurface()) ? List.of(start, depotPort.exteriorApproach()) : List.of(start);
        SurfaceAnchor outdoorStart = prefix.getLast();
        SurfaceAnchor outdoorGoal = toDepot ? depotPort.exteriorApproach() : workshopPort.exteriorApproach();
        MovementOrder outdoor = new MovementOrder(job.id(), job.workerId(), work.phase().wireTag(), 1,
                List.of(outdoorGoal), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        KnownSettlementPedestrianRoute routeKnowledge = KnownSettlementPedestrianRoute.forSettlement(
                state, job.settlementId(), List.of(
                        new KnownSettlementPedestrianRoute.Passage(depot.id(),
                                KnownSettlementPedestrianRoute.Passage.Kind.DEPOT_ACCESS),
                        new KnownSettlementPedestrianRoute.Passage(facility.id(),
                                KnownSettlementPedestrianRoute.Passage.Kind.WORKSHOP_EXTERIOR)));
        List<SurfaceAnchor> route = routeKnowledge.path(outdoorStart, outdoor);
        List<SurfaceAnchor> result = new ArrayList<>(prefix);
        result.addAll(route.subList(1, route.size()));
        if (toDepot) {
            MovementOrder serviceOrder = new MovementOrder(job.id(), job.workerId(), work.phase().wireTag(), 1L,
                    List.of(depotPort.serviceSurface()), TraversalCapability.PEDESTRIAN,
                    MovementOrder.ArrivalPolicy.EXACT_STATION);
            List<SurfaceAnchor> serviceLeg = routeKnowledge.path(depotPort.exteriorApproach(), serviceOrder);
            result.addAll(serviceLeg.subList(1, serviceLeg.size()));
        } else if (!clearing) {
            result.addAll(List.of(workshopPort.approachSurface(), workshopPort.throatSurface(),
                    workshopPort.interiorSurface(), workshopPort.inputStation(), workshopPort.workStation()));
        }
        return List.copyOf(result);
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
