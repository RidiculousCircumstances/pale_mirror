package io.farfrontier.palemirror.internal.world;

/** Crash-safe identity hand-off between a resident's home projection and a moving journey. */
public enum ResidentJourneyLeasePhase {
    RETIRE_ORIGIN,
    READY,
    RELEASED
}
