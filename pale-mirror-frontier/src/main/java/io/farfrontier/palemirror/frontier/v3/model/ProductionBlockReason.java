package io.farfrontier.palemirror.frontier.v3.model;

/** Explicit causal reason for a retrying settlement production process. */
public enum ProductionBlockReason {
    INPUT_UNAVAILABLE,
    OUTPUT_STORAGE_UNAVAILABLE,
    FACILITY_UNAVAILABLE
}
