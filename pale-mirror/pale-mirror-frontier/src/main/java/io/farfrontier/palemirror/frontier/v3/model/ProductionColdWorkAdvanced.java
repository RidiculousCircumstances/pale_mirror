package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;

/** One COLD-owned exact worker route/work transition before the deferred physical output receipt. */
public record ProductionColdWorkAdvanced(SubjectId jobId, int nextCursor, ProductionWorkProgress next,
        ActorExecutionId execution, BodyPosition expectedBody, int expectedCursor,
        ProductionWorkProgress expectedProgress, long spatialRevision) implements FrontierPayload {
    public ProductionColdWorkAdvanced {
        Objects.requireNonNull(jobId, "production cold work job");
        Objects.requireNonNull(next, "production cold work progress");
        Objects.requireNonNull(execution); Objects.requireNonNull(expectedBody); Objects.requireNonNull(expectedProgress);
        if (nextCursor < 0 || expectedCursor < 0 || spatialRevision < 1
                || execution.activityKind() != ActorActivityKind.PRODUCTION || !execution.activityOwnerId().equals(jobId))
            throw new IllegalArgumentException("production cold work has invalid cursor or foreign execution");
    }
    @Override public String type() { return "frontier.production_cold_work_advanced"; }
}
