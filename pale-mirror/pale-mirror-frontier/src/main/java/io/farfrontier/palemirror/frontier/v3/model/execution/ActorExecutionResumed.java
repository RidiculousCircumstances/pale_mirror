package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Selection commits a new execution generation for the same exact retained continuation. */
public record ActorExecutionResumed(ActorExecutionId suspended, ActorExecutionId successor, long atTick)
        implements FrontierPayload {
    public ActorExecutionResumed {
        Objects.requireNonNull(suspended); Objects.requireNonNull(successor);
        if (!suspended.actorId().equals(successor.actorId()) || suspended.activityKind() != successor.activityKind()
                || !suspended.activityOwnerId().equals(successor.activityOwnerId())
                || successor.generation() <= suspended.generation() || atTick < 0)
            throw new IllegalArgumentException("execution resume must name one exact continuation and new generation");
    }
    @Override public String type() { return "frontier.actor_execution_resumed"; }
}
