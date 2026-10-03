package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;

/** A witnessed body checkpoint or declared goal arrival under one HOT lease. */
public record ActorMovementHotObserved(SubjectId actorId, long goalRevision, long ambientRevision,
                                       BodyPosition observedBody, ActorExecutionId executionId) implements FrontierPayload {
    public ActorMovementHotObserved {
        Objects.requireNonNull(actorId, "moving actor");
        Objects.requireNonNull(executionId, "movement execution authority");
        if (!actorId.equals(executionId.actorId())) throw new IllegalArgumentException("movement execution has foreign actor");
        Objects.requireNonNull(observedBody, "observed body");
        if (goalRevision < 1L || ambientRevision < 1L)
            throw new IllegalArgumentException("movement observation needs current revisions");
    }

    @Override public String type() { return "frontier.actor_movement_hot_observed"; }
}
