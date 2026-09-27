package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
        boolean toDepot = work.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY;
        SurfaceAnchor goal = BakeryWorkGoal.current(state, job).station();
        if (start.equals(goal)) return List.of(start);
        boolean atWorkshop = start.equals(workshopPort.workStation()) || start.equals(workshopPort.inputStation())
                || start.equals(workshopPort.interiorSurface()) || start.equals(workshopPort.throatSurface())
                || start.equals(workshopPort.approachSurface());
        if (!toDepot && atWorkshop) return workshopIngress(workshopPort, start);
        List<SurfaceAnchor> prefix = atWorkshop ? workshopExit(workshopPort, start)
                : start.equals(depotPort.serviceSurface()) ? List.of(start, depotPort.exteriorApproach()) : List.of(start);
        SurfaceAnchor outdoorStart = prefix.getLast();
        SurfaceAnchor outdoorGoal = toDepot ? depotPort.exteriorApproach() : workshopPort.exteriorApproach();
        Set<BlockPosition> occupied = new HashSet<>();
        for (Settlement candidate : state.bootstrap().settlements())
            occupied.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(state.bootstrap().terrain(), candidate.structures()));
        occupied.addAll(FrontierGrayboxPlan.intactOrganOccupancy(state.bootstrap().hive().organs()));
        for (SurfaceAnchor allowed : List.of(depotPort.exteriorApproach(), depotPort.serviceSurface(),
                workshopPort.exteriorApproach(), workshopPort.approachSurface(), workshopPort.throatSurface(),
                workshopPort.interiorSurface(), workshopPort.inputStation(), workshopPort.workStation())) {
            occupied.remove(allowed.support());
            occupied.remove(allowed.support().offset(0, 1, 0));
            occupied.remove(allowed.support().offset(0, 2, 0));
        }
        // Once the worker has exited the bakery, an outdoor search must not re-enter its
        // doorway and then be sent back out by the semantic ingress/egress segment.
        if (toDepot) occupied.add(workshopPort.approachSurface().support().offset(0, 1, 0));
        state.actorLocations().forEach((id, location) -> {
            if (!id.equals(job.workerId()) && location.condition().status() == ActorLifeStatus.ALIVE)
                occupied.add(location.supportingSurface().support().offset(0, 1, 0));
        });
        Map<TerrainColumn, SurfaceAnchor> ground = SettlementPedestrianGround.localSupports(state.bootstrap(), job.settlementId());
        MovementOrder outdoor = new MovementOrder(job.id(), job.workerId(), work.phase().wireTag(), 1,
                List.of(outdoorGoal), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        List<SurfaceAnchor> route = KnownPedestrianNavigation.route(state.bootstrap(), outdoorStart, outdoor,
                occupied, (x, z) -> SettlementPedestrianGround.surveyedSupport(state.bootstrap(), ground, x, z));
        List<SurfaceAnchor> result = new ArrayList<>(prefix);
        result.addAll(route.subList(1, route.size()));
        if (toDepot) result.add(depotPort.serviceSurface());
        else result.addAll(List.of(workshopPort.approachSurface(), workshopPort.throatSurface(), workshopPort.interiorSurface(),
                workshopPort.inputStation(), workshopPort.workStation()));
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
