package io.farfrontier.palemirror.frontier.v3.api;

/** Durable lifecycle; a recovered nonterminal intent is inspected, never blindly replayed. */
public enum PhysicalIntentStatus {
    PREPARED,
    RUNNING,
    CONFIRMED,
    UNKNOWN_AFTER_RESTART
}
