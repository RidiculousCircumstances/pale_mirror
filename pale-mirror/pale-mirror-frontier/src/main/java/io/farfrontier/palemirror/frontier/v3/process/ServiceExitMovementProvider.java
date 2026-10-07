package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;

/** Optional post-service journey policy, not part of the general movement algorithm. */
public final class ServiceExitMovementProvider implements ActorMovementProvider {
    private record Exit(io.farfrontier.palemirror.frontier.v3.api.SubjectId settlementId,
                        io.farfrontier.palemirror.frontier.v3.api.SubjectId depotId, boolean retainsCaller) { }
    private static Exit exit(ActorMovement movement) {
        return switch (movement.context()) {
            case ActorMovementContext.ServiceExit e -> new Exit(e.settlementId(), e.depotId(), false);
            case ActorMovementContext.ResourceAccessExit e -> new Exit(e.settlementId(), e.depotId(), true);
            default -> throw new IllegalArgumentException("foreign service-clearance context");
        };
    }
    @Override public ActorMovementContext.Provider key() { return ActorMovementContext.Provider.SERVICE_EXIT; }
    @Override public void validate(FrontierWorldState state, ActorMovement movement) {
        var exit = exit(movement);
        if (movement.order().capability() != TraversalCapability.PEDESTRIAN)
            throw new IllegalArgumentException("service exit lacks its exact movement declaration");
        if (exit.retainsCaller()) {
            var retained = (ActorMovementContext.ResourceAccessExit) movement.context();
            state.actorExecutions().requireCurrent(movement.executionId());
            if (!movement.executionId().activityOwnerId().equals(retained.executionOwnerId())
                    || !movement.order().ownerId().equals(retained.executionOwnerId()))
                throw new IllegalArgumentException("resource clearance cannot replace its declared caller authority");
        } else if (movement.executionId().activityKind() != ActorActivityKind.SERVICE_EXIT
                || !movement.order().ownerId().equals(movement.order().actorId()))
            throw new IllegalArgumentException("standalone service exit has a foreign activity authority");
        ResidentProfile resident = state.humanPopulation().resident(movement.order().actorId());
        var container = state.inventory().containers().get(exit.depotId());
        if (resident == null || container == null || !container.ownerId().equals(exit.settlementId())
                || !FrontierWorldState.depotId(exit.settlementId()).equals(exit.depotId()))
            throw new IllegalArgumentException("service exit does not match its declared resident and depot");
    }
    @Override public FrontierWorldState start(FrontierWorldState state, ActorMovement movement, FrontierWorldStateUpdate update) {
        validate(state, movement);
        if (exit(movement).retainsCaller()) return state.withChanges(update);
        return ActorExecutionComposition.LIFECYCLE.prepareBegin(state, movement.executionId(), movement.issuedAtTick()).commit(state, update);
    }
    @Override public List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start) {
        validate(state, movement);
        var exit = exit(movement);
        return KnownServiceExitNavigation.pathFrom(state, exit.settlementId(), exit.depotId(), movement.order(), start);
    }
    @Override public List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        validate(state, movement);
        var exit = exit(movement);
        var boundary = ServiceAccessCoordinator.boundary(state, exit.depotId());
        if (boundary.occupied(route.getFirst().standingBody())) {
            for (int index = 1; index < route.size() - 1; index++) {
                if (boundary.cleared(route.get(index).standingBody())) return List.copyOf(route.subList(0, index + 1));
            }
        }
        return route;
    }
    @Override public void requireRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        validate(state, movement);
        var exit = exit(movement);
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), exit.settlementId());
        var depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        KnownPedestrianRouteKnowledge.forSettlement(state, exit.settlementId(), List.of(
                new KnownPedestrianRouteKnowledge.Passage(depot, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS))).requireRoute(route);
        if (!movement.order().arrivedAt(route.getLast())
                && !(ServiceAccessCoordinator.boundary(state, exit.depotId()).occupied(route.getFirst().standingBody())
                    && ServiceAccessCoordinator.boundary(state, exit.depotId()).cleared(route.getLast().standingBody())))
            throw new IllegalArgumentException("service exit leg does not clear its boundary or reach its goal");
    }
    @Override public ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement);
        if (exit(movement).retainsCaller()) return state.actorExecutions();
        return state.actorExecutions().finish(movement.executionId());
    }
    @Override public java.util.Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long atTick) {
        validate(state, movement);
        var exit = exit(movement);
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
        return List.of(ResidentActivityProcess.wakeAfterActivity(movement.order().actorId(), atTick));
    }
    @Override public List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long atTick) { return List.of(); }
}
