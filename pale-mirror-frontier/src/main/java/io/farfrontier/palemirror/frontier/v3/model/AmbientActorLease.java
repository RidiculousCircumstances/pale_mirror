package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * One retained per-actor HOT/COLD hand-off record. The actor identity is the lease identity;
 * every later admission increases its revision rather than creating an unbounded lease history.
 */
public record AmbientActorLease(
        SubjectId actorId, BodyPosition handoffBody, SimInstant handoffInstant, long revision,
        AmbientLeaseStatus status, AmbientGoalKind goal, BodyPosition goalBody
) {
    public AmbientActorLease {
        Objects.requireNonNull(actorId, "actor id");
        Objects.requireNonNull(handoffBody, "handoff body");
        Objects.requireNonNull(handoffInstant, "handoff instant");
        if (revision < 1L) throw new IllegalArgumentException("ambient lease revision must be positive");
        Objects.requireNonNull(status, "ambient lease status");
        Objects.requireNonNull(goal, "ambient goal");
        Objects.requireNonNull(goalBody, "ambient goal body");
    }

    public AmbientActorLease withStatus(AmbientLeaseStatus nextStatus) {
        return new AmbientActorLease(actorId, handoffBody, handoffInstant, revision, nextStatus, goal, goalBody);
    }

    public AmbientActorLease withGoal(AmbientGoalKind nextGoal, BodyPosition nextGoalBody) {
        if (status != AmbientLeaseStatus.HOT) throw new IllegalStateException("only a HOT ambient lease may change its goal");
        return new AmbientActorLease(actorId, handoffBody, handoffInstant, revision, status,
                Objects.requireNonNull(nextGoal, "ambient goal"), Objects.requireNonNull(nextGoalBody, "ambient goal body"));
    }
}
