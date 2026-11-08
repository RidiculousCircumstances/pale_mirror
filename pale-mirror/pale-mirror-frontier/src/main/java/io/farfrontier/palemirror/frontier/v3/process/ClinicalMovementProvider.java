package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import java.util.*;

/** Clinical care legs through the shared navigator, geometry and service boundaries. */
public final class ClinicalMovementProvider implements ActorMovementProvider {
    @Override public ActorMovementContext.Provider key() { return ActorMovementContext.Provider.CLINICAL; }
    private io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalTask work(FrontierWorldState state, ActorMovement movement) {
        if (!(movement.context() instanceof ActorMovementContext.ClinicalLeg leg)) throw new IllegalArgumentException("foreign clinical movement context");
        var work = io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalAuthority.require(state, movement.executionId());
        if (!work.id().equals(leg.taskId()) || work.revision() != leg.revision() || work.pending()
                || !work.order().equals(movement.order())) throw new IllegalArgumentException("stale clinical move/goal");
        state.actorExecutions().requireCurrent(work.execution()); return work;
    }
    @Override public void validate(FrontierWorldState state, ActorMovement movement) { work(state, movement); }
    @Override public FrontierWorldState start(FrontierWorldState state, ActorMovement movement, FrontierWorldStateUpdate update) {
        var rest=state.humanPopulation().restPlaces().uses().get(movement.order().actorId());
        if(rest!=null&&rest.phase()==io.farfrontier.palemirror.frontier.v3.model.rest.RestUse.Phase.RESTING)
            throw new IllegalArgumentException("clinical movement requires an observed bed exit");
        validate(state, movement); return state.withChanges(update);
    }
    @Override public KnownPedestrianRouteKnowledge placementKnowledge(FrontierWorldState state, ActorMovement movement) {
        var work = work(state, movement);
        return work.supply().map(v->ActorSupplyVisits.knowledge(state,work.home(),v.order()))
                .orElseGet(()->KnownPedestrianRouteKnowledge.forPublicJourney(state,work.home(),List.of()))
                .forActor(state,movement.order().actorId());
    }
    @Override public List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start) {
        return placementKnowledge(state, movement).plannedPath(start, movement.order());
    }
    @Override public void requireRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        placementKnowledge(state, movement).requireRoute(route);
        if (!movement.order().arrivedAt(route.getLast()) && route.size() != TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("clinical route is not its retained goal or bounded prefix");
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
        var work = work(state, movement); return work.supply().flatMap(v->ActorSupplyVisits.service(v,ServiceAccessDemand.Kind.CLINICAL_SUPPLY));
    }
    @Override public ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement) { validate(state, movement); return state.actorExecutions(); }
    @Override public ActorExecutionState interruptionAuthority(FrontierWorldState state, ActorMovement movement) { validate(state, movement); return state.actorExecutions(); }
    @Override public Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long tick) {
        var body = ActorMovementProcess.bodyAt(state, movement.order().actorId(), tick); var access = serviceApproach(state, movement);
        if (access.isEmpty()) return Optional.of(body);
        var declaration = ServiceBoundaryComposition.operational(state, access.orElseThrow().pointId());
        return declaration.isEmpty() || declaration.orElseThrow().boundary().cleared(body) ? Optional.of(body) : Optional.empty();
    }
    @Override public boolean permitsReplacement(ResidentActivityChoice.Kind next) { return next == ResidentActivityChoice.Kind.EAT; }
    @Override public List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long tick) {
        validate(state, movement); return List.of(io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalContinuation.wake(movement.order().ownerId(), tick));
    }
    @Override public List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long tick) { return arrived(state, movement, tick); }
}
