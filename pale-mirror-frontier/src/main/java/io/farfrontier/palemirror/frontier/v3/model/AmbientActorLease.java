package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * One retained per-actor HOT/COLD hand-off record. The actor identity is the lease identity;
 * every later admission increases its revision rather than creating an unbounded lease history.
 */
public record AmbientActorLease(
        SubjectId actorId, BlockPosition handoffPosition, SimInstant handoffInstant, long revision,
        AmbientLeaseStatus status, AmbientGoalKind goal, BlockPosition goalPosition
) {
    public AmbientActorLease {
        Objects.requireNonNull(actorId, "actor id");
        Objects.requireNonNull(handoffPosition, "handoff position");
        Objects.requireNonNull(handoffInstant, "handoff instant");
        if (revision < 1L) throw new IllegalArgumentException("ambient lease revision must be positive");
        Objects.requireNonNull(status, "ambient lease status");
        Objects.requireNonNull(goal, "ambient goal");
        Objects.requireNonNull(goalPosition, "ambient goal position");
    }

    public AmbientActorLease withStatus(AmbientLeaseStatus nextStatus) {
        return new AmbientActorLease(actorId, handoffPosition, handoffInstant, revision, nextStatus, goal, goalPosition);
    }

    public AmbientActorLease withGoal(AmbientGoalKind nextGoal, BlockPosition nextGoalPosition) {
        if (status != AmbientLeaseStatus.HOT) throw new IllegalStateException("only a HOT ambient lease may change its goal");
        return new AmbientActorLease(actorId, handoffPosition, handoffInstant, revision, status,
                Objects.requireNonNull(nextGoal, "ambient goal"), Objects.requireNonNull(nextGoalPosition, "ambient goal position"));
    }
}
