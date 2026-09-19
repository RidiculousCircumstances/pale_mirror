package io.farfrontier.palemirror.frontier.v3.model;

/** Closed operator outcome vocabulary.  Tags are persistent and never reused. */
public enum DiagnosticCategory {
    WAIT_OR_BLOCKED(1),
    DOMAIN_DISRUPTION(2),
    RECONCILIATION_CONFLICT(3),
    RECOVERY_UNKNOWN(4),
    CANONICAL_INVARIANT_FAILURE(5),
    ADAPTER_OR_INFRASTRUCTURE_ERROR(6);

    private final int wireTag;
    DiagnosticCategory(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }
}
