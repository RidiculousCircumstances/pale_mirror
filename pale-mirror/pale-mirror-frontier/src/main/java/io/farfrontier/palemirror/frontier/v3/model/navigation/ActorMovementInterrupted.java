package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;

import java.util.Objects;

/** Exact owner-authorized interruption, not an assertion that the old destination was reached. */
public record ActorMovementInterrupted(SubjectId actorId, long goalRevision, long atTick,
                                       BodyPosition retainedBody) implements FrontierPayload {
    public ActorMovementInterrupted {
        Objects.requireNonNull(actorId, "interrupted actor");
        Objects.requireNonNull(retainedBody, "interruption body");
        if (goalRevision < 1 || atTick < 0) throw new IllegalArgumentException("invalid interruption fence");
    }
    @Override public String type() { return "frontier.actor_movement_interrupted"; }
}
