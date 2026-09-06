package io.farfrontier.palemirror.internal.world;

/** Physical presentation state only; it never changes the canonical threat lifecycle. */
public enum EncounterState {
    NONE,
    ACTIVE,
    DEGRADED,
    CLEANED
}
