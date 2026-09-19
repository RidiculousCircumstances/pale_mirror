package io.farfrontier.palemirror.frontier.v3.model;

/** The owner action promised by one retained diagnostic fact. */
public enum DiagnosticDisposition {
    RETRY(1), REPAIR(2), INSPECT(3), ABANDON(4), QUARANTINE(5), REJECT_STALE(6);

    private final int wireTag;
    DiagnosticDisposition(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }
}
