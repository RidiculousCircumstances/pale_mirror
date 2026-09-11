package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Read-only executor instruction; it cannot create a purpose or replace a formation. */
public record ActorDirective(SubjectId actorId, SubjectId tacticalPlanId, long tacticalPlanEpoch,
                             SubjectId frontId, SceneLeaseId leaseId, TacticalRole role, MovementPlan movement) {
    public ActorDirective {
        actorId = Objects.requireNonNull(actorId, "directive actor"); tacticalPlanId = Objects.requireNonNull(tacticalPlanId, "directive plan");
        frontId = Objects.requireNonNull(frontId, "directive front"); leaseId = Objects.requireNonNull(leaseId, "directive lease");
        role = Objects.requireNonNull(role, "directive role"); movement = Objects.requireNonNull(movement, "directive movement");
        if (!actorId.equals(movement.actorId()) || !frontId.equals(movement.frontId()) || !tacticalPlanId.equals(movement.tacticalPlanId())
                || tacticalPlanEpoch != movement.tacticalPlanEpoch()) throw new IllegalArgumentException("directive does not bind its movement plan");
    }
}
