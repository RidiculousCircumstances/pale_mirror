package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;

/** Bounded current authority and one suspended continuation, not a second task queue. */
public record ActorExecution(SubjectId actorId, long generation,
                             Optional<ActorExecutionId> current,
                             Optional<ActorExecutionId> suspended) {
    public ActorExecution {
        Objects.requireNonNull(actorId, "execution actor");
        Objects.requireNonNull(current, "current execution");
        Objects.requireNonNull(suspended, "suspended execution");
        if (generation < 1L) throw new IllegalArgumentException("execution generation must be positive");
        current.ifPresent(id -> {
            if (!id.actorId().equals(actorId) || id.generation() != generation)
                throw new IllegalArgumentException("current execution has foreign or stale identity");
        });
        suspended.ifPresent(id -> {
            if (!id.actorId().equals(actorId) || id.generation() > generation || current.filter(active ->
                    id.generation() >= active.generation() || id.activityKind() == active.activityKind()
                            && id.activityOwnerId().equals(active.activityOwnerId())).isPresent())
                throw new IllegalArgumentException("suspended execution has foreign or competing identity");
        });
    }
}
