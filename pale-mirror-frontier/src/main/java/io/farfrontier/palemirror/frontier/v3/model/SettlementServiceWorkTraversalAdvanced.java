package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Observed one-edge advance of the exact service worker on its selected immutable leg. */
public record SettlementServiceWorkTraversalAdvanced(SubjectId workId, SceneLeaseId leaseId, BodyPosition observedWorker,
                                                     int nextCursor) implements FrontierPayload {
    public SettlementServiceWorkTraversalAdvanced {
        Objects.requireNonNull(workId, "service-work traversal work");
        Objects.requireNonNull(leaseId, "service-work traversal lease");
        Objects.requireNonNull(observedWorker, "service-work observed worker");
        if (nextCursor < 1) throw new IllegalArgumentException("service-work traversal cursor must advance");
    }
    @Override public String type() { return "frontier.settlement_service_work_traversal_advanced"; }
}
