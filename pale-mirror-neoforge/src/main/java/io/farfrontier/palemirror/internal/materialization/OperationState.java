package io.farfrontier.palemirror.internal.materialization;

public enum OperationState {
    PENDING,
    RUNNING,
    COMPLETED,
    /** Optional work was deliberately skipped; core physical state is still valid. */
    DEGRADED,
    BLOCKED
}
