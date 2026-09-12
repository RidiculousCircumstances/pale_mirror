package io.farfrontier.palemirror.frontier.v3.model;

/** Durable crash boundary; a flushed attempt is never itself a confirmed consequence. */
public enum FencedRecoveryPhase {
    PREPARED(1), RUNNING(2), OBSERVED(3), CONFIRMED(4), AMBIGUOUS(5);

    private final int wireTag;
    FencedRecoveryPhase(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }
}
