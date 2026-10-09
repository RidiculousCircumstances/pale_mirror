package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionWork;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import java.util.List;
import java.util.Optional;

/** Mining chooses a declared station; common geometry, pathfinding and UAE own the journey. */
public final class ExtractionMovementProvider implements ActorMovementProvider {
    @Override public ActorMovementContext.Provider key() { return ActorMovementContext.Provider.EXTRACTION; }
    private ExtractionWork work(FrontierWorldState state, ActorMovement movement) {
        if (!(movement.context() instanceof ActorMovementContext.ExtractionLeg leg))
            throw new IllegalArgumentException("mining movement has a foreign context");
        var job = ExtractionWorkAuthority.require(state, movement.executionId());
        if (!job.id().equals(leg.jobId()) || job.revision() != leg.jobRevision() || job.pending().isPresent()
                || !job.movementOrder(ExtractionWorkAuthority.site(state, job)).equals(movement.order()))
            throw new IllegalArgumentException("mining move has a stale job or goal");
        state.actorExecutions().requireCurrent(job.execution());
        return job;
    }
    @Override public void validate(FrontierWorldState state, ActorMovement movement) { work(state, movement); }
    @Override public FrontierWorldState start(FrontierWorldState state, ActorMovement movement, FrontierWorldStateUpdate update) {
        validate(state, movement); return state.withChanges(update);
    }
    @Override public KnownPedestrianRouteKnowledge placementKnowledge(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement); return KnownPedestrianRouteKnowledge.forFrontier(state);
    }
    @Override public List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start) {
        return placementKnowledge(state, movement).plannedPath(start, movement.order());
    }
    @Override public void requireRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        placementKnowledge(state, movement).requireRoute(route);
        if (!movement.order().arrivedAt(route.getLast()) && route.size() != TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("mining segment is not its declared goal or bounded prefix");
    }
    @Override public List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        return ServiceApproachSegments.bounded(state, serviceApproach(state, movement), route);
    }
    @Override public void requireColdRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route, long tick) {
        placementKnowledge(state, movement).requireRoute(route);
        ServiceApproachSegments.require(state, movement.order(), serviceApproach(state, movement), route);
    }
    @Override public long ticksPerEdge(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement); return state.bootstrap().ruleset().resourceHarvestColdTravelTicksPerEdge();
    }
    @Override public Optional<ServiceAccessDemand.Identity> serviceApproach(FrontierWorldState state, ActorMovement movement) {
        var job = work(state, movement);
        return ExtractionServiceAccess.needsAccess(job) ? Optional.of(ExtractionServiceAccess.identity(state, job)) : Optional.empty();
    }
    @Override public ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement); return state.actorExecutions();
    }
    @Override public Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long tick) {
        var job = work(state, movement);
        var body = ActorMovementProcess.bodyAt(state, movement.order().actorId(), tick);
        return ServiceAccessCoordinator.boundary(state, ExtractionWorkAuthority.site(state, job).containerId()).cleared(body)
                ? Optional.of(body) : Optional.empty();
    }
    @Override public ActorExecutionState interruptionAuthority(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement); return state.actorExecutions();
    }
    @Override public boolean permitsReplacement(ResidentActivityChoice.Kind next) { return next == ResidentActivityChoice.Kind.EAT; }
    @Override public List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long tick) {
        validate(state, movement); return List.of(ExtractionContinuation.wake(movement.order().ownerId(), tick));
    }
    @Override public List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long tick) {
        return arrived(state, movement, tick);
    }
}
