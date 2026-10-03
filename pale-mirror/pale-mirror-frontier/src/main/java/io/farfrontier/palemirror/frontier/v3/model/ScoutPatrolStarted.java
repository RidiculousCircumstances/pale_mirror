package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.Objects;

/** The scout policy explicitly issues its own activity; physical appearance never infers it. */
public record ScoutPatrolStarted(ActorExecutionId executionId) implements FrontierPayload {
    public ScoutPatrolStarted {
        Objects.requireNonNull(executionId, "scout execution");
        if (executionId.activityKind() != ActorActivityKind.SCOUT_PATROL
                || !executionId.activityOwnerId().equals(executionId.actorId()))
            throw new IllegalArgumentException("scout patrol requires its declared actor-owned execution");
    }
    @Override public String type() { return "frontier.scout_patrol_started"; }
}
