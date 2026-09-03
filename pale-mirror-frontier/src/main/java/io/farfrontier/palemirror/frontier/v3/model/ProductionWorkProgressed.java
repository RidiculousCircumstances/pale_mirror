package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Observed durable stage transition for one exact workshop job. */
public record ProductionWorkProgressed(SubjectId jobId, SceneLeaseId leaseId, BodyPosition observedWorker, ProductionWorkProgress next) implements FrontierPayload {
    public ProductionWorkProgressed {
        Objects.requireNonNull(jobId, "production work job"); Objects.requireNonNull(leaseId, "production work lease");
        Objects.requireNonNull(observedWorker, "production work observed worker"); Objects.requireNonNull(next, "production work progress");
    }
    @Override public String type() { return "frontier.production_work_progressed"; }
}
