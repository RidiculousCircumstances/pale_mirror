package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * Observed loaded-world obstruction of the one next edge in a production worker's retained
 * topology.  It deliberately names only the immutable cursor: the target surface is derived
 * from the job, so a physical executor cannot nominate a different route or entrance.
 */
public record ProductionWorkTraversalBlocked(SubjectId jobId, SceneLeaseId leaseId, BodyPosition observedWorker,
                                             int blockedNextCursor) implements FrontierPayload {
    public ProductionWorkTraversalBlocked {
        Objects.requireNonNull(jobId, "production work traversal job"); Objects.requireNonNull(leaseId, "production work lease");
        Objects.requireNonNull(observedWorker, "production work observed worker");
        if (blockedNextCursor < 1) throw new IllegalArgumentException("production work blocked cursor must be a next edge");
    }
    @Override public String type() { return "frontier.production_work_traversal_blocked"; }
}
