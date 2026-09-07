package io.farfrontier.palemirror.frontier.v3.model;

/** Bounded classification of retained physical evidence that disagrees with an emitted replica. */
public enum PhysicalReplicaConflictReason {
    FINGERPRINT_MISMATCH(1), PROVENANCE_MISMATCH(2), FINGERPRINT_AND_PROVENANCE_MISMATCH(3);

    private final int wireTag;
    PhysicalReplicaConflictReason(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }
}
