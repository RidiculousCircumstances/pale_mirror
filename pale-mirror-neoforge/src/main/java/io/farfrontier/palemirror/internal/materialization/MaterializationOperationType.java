package io.farfrontier.palemirror.internal.materialization;

/** Small, stable executor vocabulary. Policies map desired state to these operations. */
public enum MaterializationOperationType {
    ENSURE_OVERLAY,
    ENSURE_PM_ANCHOR,
    ENSURE_CRIMSON_ENCOUNTER_ACTOR,
    ENSURE_SIEGE_NODE,
    REMOVE_SIEGE_NODE,
    ENSURE_CRIMSON_SIEGE_ENTITY,
    REMOVE_CRIMSON_SIEGE_ENTITY,
    REMOVE_CRIMSON_ENCOUNTER_ACTOR,
    REMOVE_PM_ANCHOR,
    REMOVE_OVERLAY
}
