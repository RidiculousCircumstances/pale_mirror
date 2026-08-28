package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable proof that a due COLD operation step yielded to its exclusive scene lease. */
public record OperationColdSuspended(SubjectId operationId, SceneLeaseId leaseId) implements FrontierPayload {
    public OperationColdSuspended {
        Objects.requireNonNull(operationId, "suspended operation");
        Objects.requireNonNull(leaseId, "suspending scene lease");
    }
    @Override public String type() { return "frontier.operation_cold_suspended"; }
}
