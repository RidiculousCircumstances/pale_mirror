package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Shared post-interaction spatial clearance, independent of nutrition, recipes and mission policy. */
public final class ResourceAccessClearance {
    private ResourceAccessClearance() { }
    public static Optional<ActorMovement> select(FrontierWorldState state, SubjectId pointId,
                                               ActorExecutionId execution, long tick) {
        state.actorExecutions().requireCurrent(execution);
        var body = state.actorLocations().get(execution.actorId());
        if (body == null || body.condition().status() != ActorLifeStatus.ALIVE
                || state.actorMovements().containsKey(execution.actorId()) || state.humanPopulation().meals().containsKey(execution.actorId()))
            throw new IllegalArgumentException("resource clearance lacks its current exclusive caller body");
        var point = ServiceBoundaryComposition.declaration(state, pointId);
        if (point.boundary().cleared(body.body())) return Optional.empty();
        // A confirmed resource effect needs an owned clearance goal, not a new search
        // at the confirmation boundary. The movement producer/nav owner selects the reachable
        // leg later; blocked/unfinished planning cannot undo the resource receipt.
        var supported = KnownServiceExitNavigation.supportedExitStations(state, point.settlementId(), pointId, body.supportingSurface());
        if (supported.isEmpty()) throw new IllegalStateException("declared service point has no supported clearance region: " + pointId);
        var order = new MovementOrder(execution.activityOwnerId(), execution.actorId(), 0, tick + 1,
                supported.subList(0, Math.min(supported.size(), MovementOrder.MAX_LEGAL_STATIONS)),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
        return Optional.of(new ActorMovement(order, tick, new ActorMovementContext.ResourceAccessExit(
                point.identity(), execution.activityOwnerId()), execution));
    }
}
