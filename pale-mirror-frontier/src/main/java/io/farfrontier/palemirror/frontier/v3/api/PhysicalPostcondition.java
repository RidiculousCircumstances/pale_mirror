package io.farfrontier.palemirror.frontier.v3.api;

/** Exact fact an executor must inspect after a physical intent or after restart. */
public enum PhysicalPostcondition {
    CARGO_HANDOFF_OBSERVED,
    EXPLOSION_OBSERVED
}
