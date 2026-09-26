package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Terminal exact route result, retained so later policy can recover people and cargo explicitly. */
public record OperationFailed(SubjectId operationId, String reason, DiagnosticTuple diagnostic) implements FrontierPayload {
    public OperationFailed {
        Objects.requireNonNull(operationId, "operation id");
        Objects.requireNonNull(reason, "failure reason");
        if (reason.isBlank() || reason.length() > 96) throw new IllegalArgumentException("operation failure reason must be bounded and non-blank");
        diagnostic = Objects.requireNonNull(diagnostic, "operation diagnostic");
        if (diagnostic.reason() != DiagnosticReason.OPERATION_FAILED || !diagnostic.owner().id().equals(operationId) || !diagnostic.subject().id().equals(operationId)) throw new
                IllegalArgumentException("operation failure has a foreign diagnostic tuple");
    }
    @Override public String type() { return "frontier.operation_failed"; }
}
