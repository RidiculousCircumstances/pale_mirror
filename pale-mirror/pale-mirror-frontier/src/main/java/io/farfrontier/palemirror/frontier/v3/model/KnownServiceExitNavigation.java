package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.List;
import java.util.Objects;

/** Known three-dimensional pedestrian provider for a declared service-exit context. */
public final class KnownServiceExitNavigation {
    private KnownServiceExitNavigation() { }

    public static List<SurfaceAnchor> exitStations(FrontierWorldState state, SubjectId settlementId,
            SubjectId depotId, SubjectId actorId, SurfaceAnchor start) {
        return exitStations(state, settlementId, depotId, start,
                ServiceDestinationClaims.forImmediateExit(state, actorId));
    }

    /**
     * Supported semantic goals for HOT traffic arbitration, not permission to occupy them.
     * Live pedestrians must reach the shared navigator so it can request safe courtesy.
     * COLD still uses exitStations and excludes occupied or committed destinations.
     */
    public static List<SurfaceAnchor> supportedExitStations(FrontierWorldState state, SubjectId settlementId,
            SubjectId depotId, SurfaceAnchor start) {
        return exitStations(state, settlementId, depotId, start, java.util.Set.of());
    }

    private static List<SurfaceAnchor> exitStations(FrontierWorldState state, SubjectId settlementId,
            SubjectId depotId, SurfaceAnchor start, java.util.Set<SurfaceAnchor> excluded) {
        var point = ServiceBoundaryComposition.declaration(state, depotId);
        if (!point.settlementId().equals(settlementId)) throw new IllegalArgumentException("exit has a foreign service identity");
        var knowledge = ServiceBoundaryComposition.knowledge(state, point.identity());
        return ServiceClearanceTargets.exits(point.boundary(),
                start, knowledge, excluded);
    }

    public static List<SurfaceAnchor> path(FrontierWorldState state, SubjectId settlementId,
                                           SubjectId depotId, MovementOrder order) {
        ActorLocation actor = state.actorLocations().get(order.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("service exit has no living actor body");
        return pathFrom(state, settlementId, depotId, order, actor.supportingSurface());
    }

    /** Any supported exit clears access; neither parking nor return-to-work is part of service. */
    public static List<SurfaceAnchor> clearancePathFrom(FrontierWorldState state, SubjectId settlementId,
            SubjectId depotId, SubjectId ownerId, SubjectId actorId, int phase, long generation, SurfaceAnchor start) {
        var stations = exitStations(state, settlementId, depotId, actorId, start);
        for (int offset = 0; offset < stations.size(); offset += MovementOrder.MAX_LEGAL_STATIONS) {
            var batch = stations.subList(offset, Math.min(stations.size(), offset + MovementOrder.MAX_LEGAL_STATIONS));
            var order = new MovementOrder(ownerId, actorId, phase, generation, batch,
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
            try {
                var route = pathFrom(state, settlementId, depotId, order, start);
                var boundary = ServiceAccessCoordinator.boundary(state, depotId);
                for (int index = 0; index < route.size(); index++) {
                    if (boundary.cleared(route.get(index).standingBody()))
                        return List.copyOf(route.subList(0, index + 1));
                }
                throw new IllegalArgumentException("service exit route never clears its declared boundary");
            }
            catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
                // Try the remaining bounded region, without changing the semantic goal.
            }
        }
        throw new io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable(
                "no reachable supported service exit");
    }

    public static List<SurfaceAnchor> pathFrom(FrontierWorldState state, SubjectId settlementId,
                                               SubjectId depotId, MovementOrder order,
                                               SurfaceAnchor start) {
        Objects.requireNonNull(state, "service exit state");
        Objects.requireNonNull(order, "service exit movement order");
        Objects.requireNonNull(start, "service exit start");
        var point = ServiceBoundaryComposition.declaration(state, depotId);
        if (order.capability() != TraversalCapability.PEDESTRIAN
                || !point.settlementId().equals(settlementId))
            throw new IllegalArgumentException("service exit needs a local pedestrian goal");
        ActorLocation actor = state.actorLocations().get(order.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("service exit has no living actor body");
        if (ActorExecutionCoordinator.coldAvailable(state, order.actorId())) {
            var excluded = ServiceDestinationClaims.forImmediateExit(state, order.actorId());
            var available = order.legalStations().stream().filter(target -> !excluded.contains(target)).toList();
            if (available.isEmpty()) throw new io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable(
                    "service clearance region is currently occupied or reserved");
            order = new MovementOrder(order.ownerId(), order.actorId(), order.goalOrdinal(), order.goalRevision(), available,
                    order.capability(), order.arrivalPolicy());
        }
        if (order.legalStations().contains(start)) return List.of(start);
        return ServiceBoundaryComposition.knowledge(state, point.identity()).path(start, order);
    }
}
