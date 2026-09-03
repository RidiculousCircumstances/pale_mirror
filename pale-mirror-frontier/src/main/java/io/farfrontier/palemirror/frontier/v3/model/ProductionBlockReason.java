package io.farfrontier.palemirror.frontier.v3.model;

/** Explicit causal reason for a retrying settlement production process. */
public enum ProductionBlockReason {
    INPUT_UNAVAILABLE,
    OUTPUT_STORAGE_UNAVAILABLE,
    FACILITY_UNAVAILABLE,
    WORKER_UNAVAILABLE,
    FINANCE_UNAVAILABLE,
    ROUTE_BLOCKED
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
