package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import java.util.*;

/** Retained security mission goals through the common navigator and geometry. */
public final class GuardMissionMovementProvider implements ActorMovementProvider {
    @Override public ActorMovementContext.Provider key() { return ActorMovementContext.Provider.GUARD_MISSION; }
    private GuardDuty work(FrontierWorldState state, ActorMovement movement) {
        if (!(movement.context() instanceof ActorMovementContext.GuardMissionLeg leg)) throw new IllegalArgumentException("foreign guard mission movement context");
        var work = GuardAuthority.require(state, movement.executionId());
        if (!work.id().equals(leg.dutyId()) || work.revision() != leg.revision() || GuardAuthority.pending(state, work)
                || !work.missionOrder().equals(movement.order())) throw new IllegalArgumentException("stale guard mission move/goal");
        state.actorExecutions().requireCurrent(work.execution()); return work;
    }
    @Override public void validate(FrontierWorldState state, ActorMovement movement) { work(state, movement); }
    @Override public FrontierWorldState start(FrontierWorldState state, ActorMovement movement, FrontierWorldStateUpdate update) {
        validate(state, movement); return state.withChanges(update);
    }
    @Override public KnownPedestrianRouteKnowledge placementKnowledge(FrontierWorldState state, ActorMovement movement) {
        var work = work(state, movement);
        return KnownPedestrianRouteKnowledge.forPublicJourney(state, work.home(), List.of())
                .forActor(state, movement.order().actorId());
    }
    @Override public List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start) {
        return placementKnowledge(state, movement).plannedPath(start, movement.order());
    }
    @Override public void requireRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        placementKnowledge(state, movement).requireRoute(route);
        if (!movement.order().arrivedAt(route.getLast()) && route.size() != TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("guard mission route is not its retained goal or bounded prefix");
    }
    @Override public List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        return ServiceApproachSegments.bounded(state, serviceApproach(state, movement), route);
    }
    @Override public void requireColdRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route, long tick) {
        placementKnowledge(state, movement).requireRoute(route); ServiceApproachSegments.require(state, movement.order(), serviceApproach(state, movement), route);
    }
    @Override public long ticksPerEdge(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement); return ActorMovementProvider.super.ticksPerEdge(state,movement);
    }
    @Override public Optional<ServiceAccessDemand.Identity> serviceApproach(FrontierWorldState state, ActorMovement movement) {
        work(state, movement); return Optional.empty();
    }
    @Override public ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement) { validate(state, movement); return state.actorExecutions(); }
    @Override public ActorExecutionState interruptionAuthority(FrontierWorldState state, ActorMovement movement) { validate(state, movement); return state.actorExecutions(); }
    @Override public Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long tick) {
        validate(state,movement);
        return Optional.of(ActorMovementProcess.bodyAt(state,movement.order().actorId(),tick));
    }
    @Override public boolean permitsReplacement(ResidentActivityChoice.Kind next) { return next == ResidentActivityChoice.Kind.EAT; }
    @Override public List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long tick) {
        validate(state, movement); return List.of(GuardContinuation.wake(movement.order().ownerId(), tick));
    }
    @Override public List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long tick) { return arrived(state, movement, tick); }
}
