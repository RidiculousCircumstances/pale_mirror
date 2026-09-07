package io.farfrontier.palemirror.frontier.v3.model;

/** Exclusive current authority over one physical container; it is never a stock ledger. */
public enum PhysicalCustodyLeaseStatus {
    ACQUIRED(1), CHECKPOINTED(2), UNRESOLVED(3), RELEASED(4);
    private final int wireTag;
    PhysicalCustodyLeaseStatus(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }
}
