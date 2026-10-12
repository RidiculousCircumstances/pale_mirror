package io.farfrontier.palemirror.frontier.v3.model;

/** Owner-supplied scope quiescence, independent of actor incarnation and effect retirement. */
public enum SceneParticipantDeathDisposition {
    RETAIN_SCOPE, ABORT_UNBEGUN_SCOPE, DRAIN_SCOPE, CONFLICT_SCOPE;

    /** Explicitly selected by families retaining the existing coordinated-scope protocol. */
    public static SceneParticipantDeathDisposition coordinated(SceneLease lease) {
        return switch (lease.status()) {
            case HOT -> DRAIN_SCOPE;
            case PREPARED -> CONFLICT_SCOPE;
            case DRAINING, CLOSED, UNKNOWN_AFTER_RESTART, CONFLICT -> RETAIN_SCOPE;
        };
    }
}
