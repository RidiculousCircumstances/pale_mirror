package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One retained baker labor tick at the loaded station, never a recipe or cargo movement. */
public record BakeryHotWorkTick(SubjectId jobId, SceneLeaseId leaseId, BodyPosition observedWorker,
                                int nextCompletedTicks) implements FrontierPayload {
    public BakeryHotWorkTick {
        Objects.requireNonNull(jobId, "bakery labor job");
        Objects.requireNonNull(leaseId, "bakery labor lease");
        Objects.requireNonNull(observedWorker, "bakery labor body");
        if (nextCompletedTicks < 1 || nextCompletedTicks > ProductionWorkProgress.REQUIRED_PROCESSING_TICKS)
            throw new IllegalArgumentException("bakery labor must be one bounded station tick");
    }
    @Override public String type() { return "frontier.bakery_hot_work_tick"; }
}
