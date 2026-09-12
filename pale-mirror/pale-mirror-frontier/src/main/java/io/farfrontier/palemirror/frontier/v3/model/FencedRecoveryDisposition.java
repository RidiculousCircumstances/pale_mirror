package io.farfrontier.palemirror.frontier.v3.model;

/** Visible local owner action for a non-current physical consequence. */
public enum FencedRecoveryDisposition {
    RECLAIM(1), RESUME_COLD(2), INSPECT(3), RETRY(4), REPAIR(5), ABANDON(6), REJECT_STALE(7);

    private final int wireTag;
    FencedRecoveryDisposition(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }
}
