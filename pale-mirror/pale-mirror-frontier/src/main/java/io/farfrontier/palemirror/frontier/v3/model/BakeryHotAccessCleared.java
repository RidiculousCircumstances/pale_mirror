package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Physical exit from shared access after delivery; the bakery job still returns to its workshop. */
public record BakeryHotAccessCleared(SubjectId jobId, SceneLeaseId leaseId,
                                      BodyPosition observedWorker) implements FrontierPayload {
    public BakeryHotAccessCleared {
        Objects.requireNonNull(jobId, "bakery access job");
        Objects.requireNonNull(leaseId, "bakery access scene");
        Objects.requireNonNull(observedWorker, "bakery access worker body");
    }

    @Override public String type() { return "frontier.bakery_hot_access_cleared"; }
}
