package io.farfrontier.palemirror.frontier.v3.persistence;

/** Required flush boundary for a transaction before a caller may continue. */
public enum Durability {
    BATCHABLE,
    DURABLE_BEFORE_EFFECT
}
