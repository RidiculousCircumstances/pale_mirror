package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** A trusted body observation at the job's current semantic depot or station goal. */
public record BakeryHotGoalArrived(SubjectId jobId, SceneLeaseId leaseId, BakeryWorkState.Phase phase,
                                   BodyPosition observedWorker) implements FrontierPayload {
    public BakeryHotGoalArrived {
        Objects.requireNonNull(jobId, "bakery arrival job");
        Objects.requireNonNull(leaseId, "bakery arrival lease");
        Objects.requireNonNull(phase, "bakery arrival phase");
        Objects.requireNonNull(observedWorker, "bakery observed worker");
    }
    @Override public String type() { return "frontier.bakery_hot_goal_arrived"; }
}
