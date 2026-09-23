package io.farfrontier.palemirror.frontier.v3.model;

/** Exclusive current authority over one physical container; it is never a stock ledger. */
public enum PhysicalCustodyLeaseStatus {
    ACQUIRED(1), CHECKPOINTED(2), UNRESOLVED(3), RELEASED(4),
    /** Exclusive before-write fence; not permission to execute work against an observed replica. */
    PREPARING(5);
    private final int wireTag;
    PhysicalCustodyLeaseStatus(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }
}
