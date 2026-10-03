package io.farfrontier.palemirror.frontier.v3.model.execution;

import java.util.Objects;

/** One body actuator authorization. Neither version substitutes for the other. */
public record ActorActuationId(ActorBodyId body, ActorExecutionId execution) {
    public ActorActuationId {
        Objects.requireNonNull(body, "actuation body"); Objects.requireNonNull(execution, "actuation execution");
        if (!body.actorId().equals(execution.actorId()))
            throw new IllegalArgumentException("actuation joins different physical and execution actors");
    }
}
