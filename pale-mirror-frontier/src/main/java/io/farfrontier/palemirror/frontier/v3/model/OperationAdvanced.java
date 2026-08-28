package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One durable sequential COLD route advancement; the reducer moves every exact participant. */
public record OperationAdvanced(SubjectId operationId, int routeIndex, OperationStage stage) implements FrontierPayload {
    public OperationAdvanced {
        Objects.requireNonNull(operationId, "operation id");
        Objects.requireNonNull(stage, "operation stage");
        if (routeIndex < 0) throw new IllegalArgumentException("operation route index must be non-negative");
        if (stage == OperationStage.ASSEMBLING || stage == OperationStage.FAILED) throw new IllegalArgumentException("operation advancement must be en-route or arrived");
    }
    @Override public String type() { return "frontier.operation_advanced"; }
}
