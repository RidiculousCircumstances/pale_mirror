package io.farfrontier.palemirror.internal.materialization;

/** Small, stable executor vocabulary. Policies map desired state to these operations. */
public enum MaterializationOperationType {
    ENSURE_OVERLAY,
    ENSURE_PM_ANCHOR,
    ENSURE_SOURCE_ENCOUNTER_ACTOR,
    ENSURE_SOURCE_GATE_PART,
    REMOVE_SOURCE_GATE_PART,
    REMOVE_SOURCE_ENCOUNTER_ACTOR,
    REMOVE_PM_ANCHOR,
    REMOVE_OVERLAY
}
