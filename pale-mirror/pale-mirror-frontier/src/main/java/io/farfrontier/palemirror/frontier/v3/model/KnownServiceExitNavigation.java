package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Known three-dimensional pedestrian provider for a declared service-exit context. */
public final class KnownServiceExitNavigation {
    private KnownServiceExitNavigation() { }

    public static List<SurfaceAnchor> path(FrontierWorldState state, SubjectId settlementId,
                                           SubjectId depotId, MovementOrder order) {
        ActorLocation actor = state.actorLocations().get(order.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("service exit has no living actor body");
        return pathFrom(state, settlementId, depotId, order, actor.supportingSurface());
    }

    public static List<SurfaceAnchor> pathFrom(FrontierWorldState state, SubjectId settlementId,
                                               SubjectId depotId, MovementOrder order,
                                               SurfaceAnchor start) {
        Objects.requireNonNull(state, "service exit state");
        Objects.requireNonNull(order, "service exit movement order");
        Objects.requireNonNull(start, "service exit start");
        if (order.capability() != TraversalCapability.PEDESTRIAN
                || order.arrivalPolicy() != MovementOrder.ArrivalPolicy.EXACT_STATION
                || !depotId.equals(FrontierWorldState.depotId(settlementId)))
            throw new IllegalArgumentException("service exit needs an exact local pedestrian goal");
        SurfaceAnchor destination = order.legalStations().getFirst();
        ActorLocation actor = state.actorLocations().get(order.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("service exit has no living actor body");
        if (start.equals(destination)) return List.of(start);
        Set<BlockPosition> occupied = new HashSet<>();
        for (Settlement candidate : state.bootstrap().settlements())
            occupied.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(
                    state.bootstrap().terrain(), candidate.structures()));
        occupied.addAll(FrontierGrayboxPlan.intactOrganOccupancy(state.bootstrap().hive().organs()));
        occupied.addAll(state.physicalDeltas().keySet());
        clear(occupied, start);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId);
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        clear(occupied, port.serviceSurface());
        clear(occupied, port.exteriorApproach());
        state.actorLocations().forEach((id, location) -> {
            if (!id.equals(order.actorId()) && location.condition().status() == ActorLifeStatus.ALIVE)
                occupied.add(location.supportingSurface().support().offset(0, 1, 0));
        });
        Map<TerrainColumn, SurfaceAnchor> known = SettlementPedestrianGround.localSupports(
                state.bootstrap(), settlementId);
        return KnownPedestrianNavigation.route(state.bootstrap(), start, order, occupied,
                (x, z) -> SettlementPedestrianGround.surveyedSupport(state.bootstrap(), known, x, z));
    }

    private static void clear(Set<BlockPosition> occupied, SurfaceAnchor surface) {
        occupied.remove(surface.support());
        occupied.remove(surface.support().offset(0, 1, 0));
        occupied.remove(surface.support().offset(0, 2, 0));
    }
}
