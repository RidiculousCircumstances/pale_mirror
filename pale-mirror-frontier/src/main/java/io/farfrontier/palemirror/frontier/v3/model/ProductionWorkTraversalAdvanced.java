package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Observed one-edge advance of the immutable workshop work corridor. */
public record ProductionWorkTraversalAdvanced(SubjectId jobId, SceneLeaseId leaseId, BodyPosition observedWorker, int nextCursor) implements FrontierPayload {
    public ProductionWorkTraversalAdvanced {
        Objects.requireNonNull(jobId, "production work traversal job"); Objects.requireNonNull(leaseId, "production work lease");
        Objects.requireNonNull(observedWorker, "production work observed worker");
        if (nextCursor < 1) throw new IllegalArgumentException("production work traversal cursor must advance");
    }
    @Override public String type() { return "frontier.production_work_traversal_advanced"; }
}
