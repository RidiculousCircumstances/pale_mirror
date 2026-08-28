package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Terminal exact route result, retained so later policy can recover people and cargo explicitly. */
public record OperationFailed(SubjectId operationId, String reason) implements FrontierPayload {
    public OperationFailed {
        Objects.requireNonNull(operationId, "operation id");
        Objects.requireNonNull(reason, "failure reason");
        if (reason.isBlank() || reason.length() > 96) throw new IllegalArgumentException("operation failure reason must be bounded and non-blank");
    }
    @Override public String type() { return "frontier.operation_failed"; }
}
