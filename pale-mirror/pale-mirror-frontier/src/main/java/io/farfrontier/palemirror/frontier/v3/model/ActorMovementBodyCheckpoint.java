package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityBodyCheckpoint;
import java.util.LinkedHashMap;

/** Keep the semantic goal, but never resume a pre-HOT clock after an actual physical departure. */
public final class ActorMovementBodyCheckpoint implements ActorActivityBodyCheckpoint {
    @Override public Acknowledgement acknowledge(Request request) {
        var state = request.expectedState();
        var movement = state.actorMovements().get(request.execution().actorId());
        if (movement == null) return new Acknowledgement(request, FrontierWorldStateUpdate.begin());
        if (!movement.executionId().equals(request.execution()))
            throw new IllegalArgumentException("movement body checkpoint has a foreign execution");
        var movements = new LinkedHashMap<>(state.actorMovements());
        movements.put(request.execution().actorId(), movement.withoutColdTravel());
        return new Acknowledgement(request, FrontierWorldStateUpdate.begin().actorMovements(movements));
    }
}
