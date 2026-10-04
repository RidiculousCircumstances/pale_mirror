package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation;

/** A declared goal arrival following an independent common body inspection. */
public record ActorMovementHotObserved(SubjectId actorId, long goalRevision, long ambientRevision,
                                       BodyPosition observedBody, ActorHotObservation observation) implements FrontierPayload {
    public ActorMovementHotObserved {
        Objects.requireNonNull(actorId, "moving actor");
        Objects.requireNonNull(observation, "captured movement authority");
        if (!actorId.equals(observation.actuation().execution().actorId())
                || ambientRevision != observation.scopeRevision())
            throw new IllegalArgumentException("movement has foreign actor or scope");
        Objects.requireNonNull(observedBody, "observed body");
        if (goalRevision < 1L || ambientRevision < 1L)
            throw new IllegalArgumentException("movement observation needs current revisions");
    }

    @Override public String type() { return "frontier.actor_movement_hot_observed"; }
    public ActorExecutionId executionId() { return observation.actuation().execution(); }
}
