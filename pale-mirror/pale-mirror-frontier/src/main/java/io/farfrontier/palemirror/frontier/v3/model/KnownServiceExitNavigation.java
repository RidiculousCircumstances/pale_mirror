package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.List;
import java.util.Objects;

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
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId);
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        return KnownSettlementPedestrianRoute.path(state, settlementId, start, order,
                List.of(new KnownSettlementPedestrianRoute.Passage(depot.id(),
                        KnownSettlementPedestrianRoute.Passage.Kind.DEPOT_ACCESS)));
    }
}
