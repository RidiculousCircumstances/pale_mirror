package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;

/** Exact owner-authorized interruption, not an assertion that the old destination was reached. */
public record ActorMovementInterrupted(SubjectId actorId, long goalRevision, long atTick,
                                       BodyPosition retainedBody, ActorExecutionId executionId) implements FrontierPayload {
    public ActorMovementInterrupted {
        Objects.requireNonNull(actorId, "interrupted actor");
        Objects.requireNonNull(executionId, "movement execution authority");
        if (!actorId.equals(executionId.actorId())) throw new IllegalArgumentException("movement execution has foreign actor");
        Objects.requireNonNull(retainedBody, "interruption body");
        if (goalRevision < 1 || atTick < 0) throw new IllegalArgumentException("invalid interruption fence");
    }
    @Override public String type() { return "frontier.actor_movement_interrupted"; }
}
