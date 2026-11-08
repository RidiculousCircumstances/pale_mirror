package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.Map;

/** Shared COLD body boundary for an owner-validated nearby pose interaction. Never a walking route. */
public final class ActorLocalContact {
    public static Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ActorLocation> change(
            FrontierWorldState state, ActorExecutionId execution, BodyPosition expected, BodyPosition destination) {
        state.actorExecutions().requireCurrent(execution);
        var actor = state.actorLocations().get(execution.actorId());
        if (actor == null || !actor.body().equals(expected) || actor.condition().status() != ActorLifeStatus.ALIVE
                || !ActorExecutionCoordinator.coldAvailable(state, execution.actorId())
                || state.actorMovements().containsKey(execution.actorId())
                || ActorInventoryInteractionFences.pending(state, execution.actorId())
                || expected.supportingSurface().manhattanDistance(destination.supportingSurface()) > 4)
            throw new IllegalArgumentException("local contact lacks exclusive nearby COLD body authority");
        return ImmutableLookupMap.withEntry(state.actorLocations(), execution.actorId(), actor.withBody(destination));
    }
    private ActorLocalContact() { }
}
