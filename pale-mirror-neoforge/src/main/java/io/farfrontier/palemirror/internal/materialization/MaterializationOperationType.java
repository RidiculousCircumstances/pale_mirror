package io.farfrontier.palemirror.internal.materialization;

/** Small, stable executor vocabulary. Policies map desired state to these operations. */
public enum MaterializationOperationType {
    ENSURE_OVERLAY,
    ENSURE_TEST_THREAT_CONTROLLER,
    REMOVE_TEST_THREAT_CONTROLLER,
    REMOVE_OVERLAY
}
