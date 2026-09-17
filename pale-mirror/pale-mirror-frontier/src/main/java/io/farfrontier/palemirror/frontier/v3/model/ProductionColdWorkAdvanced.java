package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One COLD-owned exact worker route/work transition before the deferred physical output receipt. */
public record ProductionColdWorkAdvanced(SubjectId jobId, int nextCursor, ProductionWorkProgress next) implements FrontierPayload {
    public ProductionColdWorkAdvanced {
        Objects.requireNonNull(jobId, "production cold work job");
        Objects.requireNonNull(next, "production cold work progress");
        if (nextCursor < 0) throw new IllegalArgumentException("production cold work cursor is invalid");
    }
    @Override public String type() { return "frontier.production_cold_work_advanced"; }
}
