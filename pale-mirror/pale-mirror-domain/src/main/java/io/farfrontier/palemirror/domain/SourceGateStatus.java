package io.farfrontier.palemirror.domain;

/** Source-neutral clearance state that protects a PM controller while active. */
public enum SourceGateStatus {
    INACTIVE,
    PENDING,
    ACTIVE,
    BYPASSED,
    UNSEALED;

    public boolean protectsController() {
        return this == PENDING || this == ACTIVE;
    }
}
