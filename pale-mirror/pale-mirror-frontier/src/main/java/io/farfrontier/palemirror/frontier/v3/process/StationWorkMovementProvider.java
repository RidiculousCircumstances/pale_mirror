package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import java.util.*;

/** Production declares goals and effects; common movement alone owns their HOT/COLD travel. */
public final class StationWorkMovementProvider implements ActorMovementProvider {
    @Override public ActorMovementContext.Provider key() { return ActorMovementContext.Provider.STATION_WORK; }
    private ProductionJob job(FrontierWorldState state, ActorMovement movement) {
        if (!(movement.context() instanceof ActorMovementContext.StationWorkLeg leg))
            throw new IllegalArgumentException("foreign station movement context");
        var job = state.productionJobs().get(leg.jobId());
        var work = job == null ? null : job.stationWork().orElse(null);
        state.actorExecutions().requireCurrent(movement.executionId());
        if (work == null || !job.workerId().equals(movement.order().actorId()) || work.phase() != leg.phase()
                || work.materials().tripOrdinal() != leg.tripOrdinal() || work.pendingPhysicalStep().isPresent()
                || !ProductionServiceAccess.point(state, job).equals(leg.pointId())
                || movement.order().goalOrdinal() != work.goalOrdinal() || movement.order().goalRevision() != 1L)
            throw new IllegalArgumentException("stale station movement phase, trip or selected access point");
        return job;
    }
    @Override public void validate(FrontierWorldState state, ActorMovement movement) { job(state, movement); }
    @Override public FrontierWorldState start(FrontierWorldState state, ActorMovement movement, FrontierWorldStateUpdate update) {
        var job = job(state, movement); var work = job.stationWork().orElseThrow();
        if (work.phase() != StationWorkState.Phase.DELIVERED) {
            if (!StationWorkGoal.current(state, job).movementOrder().equals(movement.order()))
                throw new IllegalArgumentException("station movement has a foreign declared goal");
        } else {
            var boundary = ServiceBoundaryComposition.declaration(state, ProductionServiceAccess.point(state, job)).boundary();
            if (movement.order().legalStations().stream().anyMatch(target -> !boundary.cleared(target.standingBody())))
                throw new IllegalArgumentException("station exit does not clear its declared service boundary");
        }
        return state.withChanges(update);
    }
    @Override public KnownPedestrianRouteKnowledge placementKnowledge(FrontierWorldState state, ActorMovement movement) {
        var job = job(state, movement);
        return job.stationWork().orElseThrow().phase() == StationWorkState.Phase.DELIVERED
                ? ServiceBoundaryComposition.knowledge(state, ServiceBoundaryComposition.declaration(state,
                    StationWorkStorage.destination(job)).identity()).forActor(state, job.workerId())
                : StationWorkNavigation.journeyKnowledge(state, job);
    }
    @Override public List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start) {
        var job = job(state, movement);
        return job.stationWork().orElseThrow().phase() == StationWorkState.Phase.DELIVERED
                ? KnownServiceExitNavigation.plannedPathFrom(state, job.settlementId(), StationWorkStorage.destination(job), movement.order(), start)
                : placementKnowledge(state, movement).plannedPath(start, movement.order());
    }
    @Override public void requireRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        placementKnowledge(state, movement).requireRoute(route);
        if (!movement.order().arrivedAt(route.getLast()) && route.size() != TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("station route is not its declared goal or bounded prefix");
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
        var job = job(state, movement);
        return job.stationWork().orElseThrow().phase() == StationWorkState.Phase.DELIVERED ? Optional.empty()
                : Optional.of(ProductionServiceAccess.identity(job, ProductionServiceAccess.point(state, job)));
    }
    @Override public ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement) { validate(state, movement); return state.actorExecutions(); }
    @Override public ActorExecutionState interruptionAuthority(FrontierWorldState state, ActorMovement movement) { validate(state, movement); return state.actorExecutions(); }
    @Override public Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long tick) {
        var job = job(state, movement); var body = ActorMovementProcess.bodyAt(state, job.workerId(), tick);
        var declaration = ServiceBoundaryComposition.operational(state, ProductionServiceAccess.point(state, job));
        return declaration.isEmpty() || declaration.orElseThrow().boundary().cleared(body) ? Optional.of(body) : Optional.empty();
    }
    @Override public boolean permitsReplacement(ResidentActivityChoice.Kind next) { return next == ResidentActivityChoice.Kind.EAT; }
    @Override public List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long tick) {
        return List.of(ProductionContinuation.wake(job(state, movement), tick));
    }
    @Override public List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long tick) { return arrived(state, movement, tick); }
}
