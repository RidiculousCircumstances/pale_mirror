package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.List;
import java.util.Optional;

/** The visit supplies only a semantic service goal; common geometry/navigation executes it. */
public final class DefenderEquipmentReturnMovementProvider implements ActorMovementProvider {
    @Override public ActorMovementContext.Provider key() { return ActorMovementContext.Provider.DEFENDER_EQUIPMENT_RETURN; }
    private DefenderEquipmentReturnVisit visit(FrontierWorldState state, ActorMovement movement) {
        if (!(movement.context() instanceof ActorMovementContext.DefenderEquipmentReturn leg))
            throw new IllegalArgumentException("equipment move has a foreign nominal context");
        var visit = DefenderEquipmentReturnAuthority.require(state, movement.executionId());
        state.actorExecutions().requireCurrent(movement.executionId());
        if (!leg.assaultId().equals(visit.ownerId()) || !leg.transferId().equals(visit.transferId())
                || !visit.order().equals(Optional.of(movement.order())) || DefenderEquipmentReturnAuthority.pendingEffect(state, visit))
            throw new IllegalArgumentException("equipment move has a substituted goal or unsettled effect");
        return visit;
    }
    @Override public void validate(FrontierWorldState state, ActorMovement movement) { visit(state, movement); }
    @Override public FrontierWorldState start(FrontierWorldState state, ActorMovement movement, FrontierWorldStateUpdate update) {
        validate(state, movement); return state.withChanges(update);
    }
    @Override public KnownPedestrianRouteKnowledge placementKnowledge(FrontierWorldState state, ActorMovement movement) {
        return ResourceStorageNavigation.forEndpoint(state, visit(state, movement).access().endpoint());
    }
    @Override public List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start) {
        return placementKnowledge(state, movement).plannedPath(start, movement.order());
    }
    @Override public void requireRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        placementKnowledge(state, movement).requireRoute(route);
        if (!movement.order().arrivedAt(route.getLast()) && route.size() != TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("equipment route is neither its goal nor a bounded prefix");
        var visit = visit(state, movement);
        if (visit.phase() == DefenderEquipmentReturnVisit.Phase.CLEARANCE && !visit.access().accessBoundary().allowsWaitingRoute(route))
            throw new IllegalArgumentException("equipment clearance re-enters its released access footprint");
    }
    @Override public Optional<ServiceAccessDemand.Identity> serviceApproach(FrontierWorldState state, ActorMovement movement) {
        var visit = visit(state, movement);
        return visit.phase() == DefenderEquipmentReturnVisit.Phase.APPROACH ? Optional.of(visit.request().identity()) : Optional.empty();
    }
    @Override public List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        return ServiceApproachSegments.bounded(state, serviceApproach(state, movement), route);
    }
    @Override public void requireColdRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route, long tick) {
        placementKnowledge(state, movement).requireRoute(route);
        ServiceApproachSegments.require(state, movement.order(), serviceApproach(state, movement), route);
        if (visit(state, movement).phase() == DefenderEquipmentReturnVisit.Phase.CLEARANCE
                && !visit(state, movement).access().accessBoundary().allowsWaitingRoute(route))
            throw new IllegalArgumentException("equipment COLD clearance re-enters its service footprint");
    }
    @Override public long ticksPerEdge(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement); return state.bootstrap().ruleset().resourceHarvestColdTravelTicksPerEdge();
    }
    @Override public ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement) { validate(state, movement); return state.actorExecutions(); }
    @Override public Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long tick) {
        var visit = visit(state, movement);
        var actual = ActorMovementProcess.bodyAt(state, visit.actorId(), tick);
        return visit.access().accessBoundary().cleared(actual) ? Optional.of(actual) : Optional.empty();
    }
    @Override public ActorExecutionState interruptionAuthority(FrontierWorldState state, ActorMovement movement) { return arrivalAuthority(state, movement); }
    @Override public boolean permitsReplacement(ResidentActivityChoice.Kind next) { return next == ResidentActivityChoice.Kind.EAT; }
    @Override public List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long tick) {
        var visit = visit(state, movement);
        return List.of(DefenderEquipmentReturnContinuation.wake(state.strategicPlans().settlementAssaults().get(visit.ownerId()), tick));
    }
    @Override public List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long tick) { return arrived(state, movement, tick); }
}
