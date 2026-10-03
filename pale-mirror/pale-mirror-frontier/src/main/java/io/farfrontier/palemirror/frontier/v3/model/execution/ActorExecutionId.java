package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Exact execution authority; generation is independent of route revision and physical epoch. */
public record ActorExecutionId(SubjectId actorId, ActorActivityKind activityKind,
                               SubjectId activityOwnerId, long generation) {
    public ActorExecutionId {
        Objects.requireNonNull(actorId, "execution actor");
        Objects.requireNonNull(activityKind, "execution activity kind");
        Objects.requireNonNull(activityOwnerId, "execution activity owner");
        if (generation < 1L) throw new IllegalArgumentException("execution generation must be positive");
    }
}
