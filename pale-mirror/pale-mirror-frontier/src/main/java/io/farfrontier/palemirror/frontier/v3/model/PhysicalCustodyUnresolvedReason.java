package io.farfrontier.palemirror.frontier.v3.model;

/** Bounded local classification retained when a physical custodian cannot be safely released. */
public enum PhysicalCustodyUnresolvedReason {
    OBSERVATION_MISMATCH(1), RESTART_AMBIGUITY(2), PROVIDER_LOST(3);
    private final int wireTag;
    PhysicalCustodyUnresolvedReason(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }
}
