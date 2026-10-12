package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Objects;

/** Observed HOT labour boundary; station transformation is a separate physical effect. */
public record BakeryWorkChanged(SubjectId jobId, SceneLeaseId leaseId, ActorExecutionId executionId,
        BodyPosition observedWorker, BakeryLabourChange change) implements FrontierPayload {
    public BakeryWorkChanged {
        Objects.requireNonNull(jobId); Objects.requireNonNull(leaseId); Objects.requireNonNull(executionId);
        Objects.requireNonNull(observedWorker); Objects.requireNonNull(change);
        if (executionId.activityKind() != ActorActivityKind.PRODUCTION || !executionId.activityOwnerId().equals(jobId))
            throw new IllegalArgumentException("bakery work needs its exact execution owner");
    }
    @Override public String type() { return "frontier.bakery_work_changed"; }
}
