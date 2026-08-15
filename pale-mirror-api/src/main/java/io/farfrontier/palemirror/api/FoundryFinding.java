package io.farfrontier.palemirror.api;

import java.util.Objects;

/** One bounded, locatable materialization defect or diagnostic observation. */
public record FoundryFinding(String ruleId, FoundrySeverity severity, FoundryAuditPhase phase,
                             String targetKind, String targetId, String dimensionId,
                             VisualPoint position, String message, String remediation) {
    public FoundryFinding {
        require(ruleId, "ruleId");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(phase, "phase");
        require(targetKind, "targetKind");
        require(targetId, "targetId");
        require(dimensionId, "dimensionId");
        Objects.requireNonNull(position, "position");
        require(message, "message");
        remediation = remediation == null ? "" : remediation;
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
