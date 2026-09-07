package io.farfrontier.palemirror.frontier.v3.model;

/** Persistent, non-authoritative state of one emitted physical container replica. */
public enum PhysicalReplicaState {
    EXPECTED(1), OBSERVED_CURRENT(2), CONFLICT(3);

    private final int wireTag;
    PhysicalReplicaState(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }

    public boolean mayTransitionTo(PhysicalReplicaState next) {
        return switch (this) {
            case EXPECTED -> next == OBSERVED_CURRENT || next == CONFLICT;
            case OBSERVED_CURRENT -> next == EXPECTED;
            case CONFLICT -> false;
        };
    }
}
