package io.farfrontier.palemirror.internal.economy;

public enum ResourceTransferState {
    PREPARED,
    PHYSICAL_RESERVED,
    DOMAIN_APPLIED,
    COMPLETED,
    BLOCKED,
    CANCELLED;

    public boolean terminal() { return this == COMPLETED || this == BLOCKED || this == CANCELLED; }
}
