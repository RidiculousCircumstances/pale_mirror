package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.LinkedHashMap;

/** A parent may withdraw only its own order, without teleporting or completing the activity. */
public final class ActorMovementWithdrawal {
    private ActorMovementWithdrawal() { }
    public static FrontierWorldState apply(FrontierWorldState state, ActorExecutionId parent) {
        var movement = state.actorMovements().get(parent.actorId());
        if (movement == null) return state;
        if (!movement.executionId().equals(parent)) throw new IllegalArgumentException("movement withdrawal has a competing parent");
        state.actorExecutions().requireCurrent(parent);
        var next = new LinkedHashMap<>(state.actorMovements()); next.remove(parent.actorId());
        return state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(next));
    }
}
