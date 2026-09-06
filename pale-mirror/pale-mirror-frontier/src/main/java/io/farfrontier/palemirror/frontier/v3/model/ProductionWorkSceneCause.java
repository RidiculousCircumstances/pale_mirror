package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact workshop job temporarily owned by a naturally loaded work scene. */
public record ProductionWorkSceneCause(SubjectId jobId) implements SceneCause {
    public ProductionWorkSceneCause {
        Objects.requireNonNull(jobId, "production-work scene job");
        if (!jobId.value().startsWith("job:production-")) throw new IllegalArgumentException("production-work scene requires a production job");
    }
    @Override public SceneCauseKind kind() { return SceneCauseKind.PRODUCTION_WORK; }
}
