package io.farfrontier.palemirror.api;

/** Stable checkpoints of the authored-world lifecycle inspected by Foundry. */
public enum FoundryAuditPhase {
    PLAN,
    COMPILED,
    MATERIALIZED,
    SETTLED,
    RELOADED
}
