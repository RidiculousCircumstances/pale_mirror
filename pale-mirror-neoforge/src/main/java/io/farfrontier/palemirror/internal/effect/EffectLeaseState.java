package io.farfrontier.palemirror.internal.effect;

/** Durable lifecycle for one PM-authorized physical effect. */
public enum EffectLeaseState {
    PLANNED,
    RUNNING,
    COMPLETED,
    FAILED,
    UNKNOWN_AFTER_RESTART,
    EXPIRED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == UNKNOWN_AFTER_RESTART || this == EXPIRED;
    }
}
