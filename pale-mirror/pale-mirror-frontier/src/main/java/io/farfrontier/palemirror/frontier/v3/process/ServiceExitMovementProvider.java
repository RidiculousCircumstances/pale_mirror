package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;

/** Optional post-service journey policy, not part of the general movement algorithm. */
public final class ServiceExitMovementProvider implements ActorMovementProvider {
    @Override public ActorMovementContext.Provider key() { return ActorMovementContext.Provider.SERVICE_EXIT; }
    @Override public void validate(FrontierWorldState state, ActorMovement movement) {
        if (!(movement.context() instanceof ActorMovementContext.ServiceExit exit)
                || movement.executionId().activityKind() != ActorActivityKind.SERVICE_EXIT
                || !movement.order().ownerId().equals(movement.order().actorId())
                || movement.order().capability() != TraversalCapability.PEDESTRIAN)
            throw new IllegalArgumentException("service exit lacks its exact movement declaration");
        ResidentProfile resident = state.humanPopulation().resident(movement.order().actorId());
        if (resident == null || !resident.settlementId().equals(exit.settlementId())
                || !FrontierWorldState.depotId(exit.settlementId()).equals(exit.depotId()))
            throw new IllegalArgumentException("service exit does not match its declared resident and depot");
    }
    @Override public FrontierWorldState start(FrontierWorldState state, ActorMovement movement, FrontierWorldStateUpdate update) {
        validate(state, movement);
        return ActorExecutionComposition.LIFECYCLE.prepareBegin(state, movement.executionId(), movement.issuedAtTick()).commit(state, update);
    }
    @Override public List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start) {
        validate(state, movement);
        var exit = (ActorMovementContext.ServiceExit) movement.context();
        return KnownServiceExitNavigation.pathFrom(state, exit.settlementId(), exit.depotId(), movement.order(), start);
    }
    @Override public List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        validate(state, movement);
        var exit = (ActorMovementContext.ServiceExit) movement.context();
        var boundary = ServiceAccessCoordinator.boundary(state, exit.depotId());
        if (boundary.occupied(route.getFirst().standingBody())) {
            for (int index = 1; index < route.size() - 1; index++) {
                if (boundary.cleared(route.get(index).standingBody())) return List.copyOf(route.subList(0, index + 1));
            }
        }
        return route;
    }
    @Override public ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement);
        return state.actorExecutions().finish(movement.executionId());
    }
    @Override public java.util.Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long atTick) {
        validate(state, movement);
        var exit = (ActorMovementContext.ServiceExit) movement.context();
        var current = ActorMovementProcess.bodyAt(state, movement.order().actorId(), atTick);
        return ServiceAccessCoordinator.boundary(state, exit.depotId()).cleared(current) ? java.util.Optional.of(current) : java.util.Optional.empty();
    }
    @Override public ActorExecutionState interruptionAuthority(FrontierWorldState state, ActorMovement movement) {
        return arrivalAuthority(state, movement);
    }
    @Override public boolean permitsReplacement(ResidentActivityChoice.Kind next) {
        return next == ResidentActivityChoice.Kind.WORK || next == ResidentActivityChoice.Kind.EAT;
    }
    @Override public List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long atTick) {
        return List.of(ResidentActivityProcess.wakeAfterMeal(movement.order().actorId(), atTick));
    }
    @Override public List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long atTick) { return List.of(); }
}
