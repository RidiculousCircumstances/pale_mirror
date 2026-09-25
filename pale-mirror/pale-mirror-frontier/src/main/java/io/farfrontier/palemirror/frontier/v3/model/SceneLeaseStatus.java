package io.farfrontier.palemirror.frontier.v3.model;

/** Durable lifecycle of an exclusive execution-location hand-off. */
public enum SceneLeaseStatus {
    PREPARED, HOT, DRAINING, CLOSED, UNKNOWN_AFTER_RESTART, CONFLICT;

    boolean canTransitionTo(SceneLeaseStatus next) {
        return switch (this) {
            case PREPARED -> next == HOT || next == UNKNOWN_AFTER_RESTART || next == CONFLICT;
            case HOT -> next == DRAINING || next == UNKNOWN_AFTER_RESTART || next == CONFLICT;
            case DRAINING -> next == UNKNOWN_AFTER_RESTART || next == CONFLICT;
            case CLOSED -> false;
            // Reclaim requires every exact owned body. An adapter may first complete
            // explicitly permitted, never-started initial admission; unknown absence
            // alone cannot authorize creation or replay an attempted admission.
            // PREPARED is recovery of an already materialized pre-effect assembly: the same
            // bodies were observed again, but no treatment effect had become eligible yet.
            case UNKNOWN_AFTER_RESTART -> next == PREPARED || next == HOT || next == DRAINING;
            // A loaded-world obstruction is not restart evidence. It remains visible until a
            // future attributed conflict-resolution boundary has admitted fresh preparation.
            case CONFLICT -> next == PREPARED;
        };
    }


    public int wireTag() { return FrontierWireTags.tag(this); }
}
