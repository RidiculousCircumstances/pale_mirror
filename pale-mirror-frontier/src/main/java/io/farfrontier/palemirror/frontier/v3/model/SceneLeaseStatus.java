package io.farfrontier.palemirror.frontier.v3.model;

/** Durable lifecycle of an exclusive execution-location hand-off. */
public enum SceneLeaseStatus {
    PREPARED, HOT, DRAINING, CLOSED, UNKNOWN_AFTER_RESTART;

    boolean canTransitionTo(SceneLeaseStatus next) {
        return switch (this) {
            case PREPARED -> next == HOT || next == UNKNOWN_AFTER_RESTART;
            case HOT -> next == DRAINING || next == UNKNOWN_AFTER_RESTART;
            case DRAINING -> next == UNKNOWN_AFTER_RESTART;
            case CLOSED -> false;
            // Reclaim is observation-only: the adapter must find every already-owned body.
            case UNKNOWN_AFTER_RESTART -> next == HOT;
        };
    }
}
