package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.Optional;

/** Common movement authority checkpoints a known landing after native airborne departure. */
final class ActorMovementGrounding {
    private ActorMovementGrounding() { }
    static Optional<ActorMovementGrounded> plan(FrontierWorldState state, ActorMovement movement, long tick) {
        var id = movement.order().actorId(); var actor = state.actorLocations().get(id);
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || movement.coldTravel().isPresent()
                || tick < movement.issuedAtTick() || !ActorExecutionCoordinator.coldAvailable(state, id)) return Optional.empty();
        state.actorExecutions().requireCurrent(movement.executionId());
        var geometry = ActorMovementProviders.require(movement).placementKnowledge(state, movement).geometry();
        return PedestrianVerticalSettlement.confirmed(actor.body(), geometry, actor.navigationProfile())
                .map(landing -> new ActorMovementGrounded(movement.executionId(), movement.order().goalRevision(),
                        tick, actor.body(), landing));
    }
    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ActorMovementGrounded event) {
        var movement = state.actorMovements().get(subject);
        if (!subject.equals(event.execution().actorId()) || movement == null
                || !movement.executionId().equals(event.execution())
                || !plan(state, movement, event.atTick()).equals(Optional.of(event)))
            throw new IllegalArgumentException("movement settlement lacks exact COLD authority, pose or clear landing");
        return state.withActorBody(subject, event.landing().standingBody());
    }
}
