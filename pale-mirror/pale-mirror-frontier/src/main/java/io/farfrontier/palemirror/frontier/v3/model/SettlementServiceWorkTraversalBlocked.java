package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Loaded collision at the sole retained next service-work edge; it never nominates a detour. */
public record SettlementServiceWorkTraversalBlocked(SubjectId workId, SceneLeaseId leaseId, BodyPosition observedWorker,
                                                    int blockedNextCursor) implements FrontierPayload {
    public SettlementServiceWorkTraversalBlocked {
        Objects.requireNonNull(workId, "service-work block work");
        Objects.requireNonNull(leaseId, "service-work block lease");
        Objects.requireNonNull(observedWorker, "service-work observed worker");
        if (blockedNextCursor < 1) throw new IllegalArgumentException("service-work blocked cursor must be a next edge");
    }
    @Override public String type() { return "frontier.settlement_service_work_traversal_blocked"; }
}
