package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Selection admits passive presence, without inventing work, locomotion or a physical body. */
public record ActorPresenceStarted(ActorExecutionId execution, long atTick) implements FrontierPayload {
    public ActorPresenceStarted {
        Objects.requireNonNull(execution, "presence execution");
        if (execution.activityKind() != ActorActivityKind.PRESENCE
                || !execution.actorId().equals(execution.activityOwnerId()) || atTick < 0)
            throw new IllegalArgumentException("presence requires its complete actor-owned declaration");
    }
    @Override public String type() { return "frontier.actor_presence_started"; }
}
