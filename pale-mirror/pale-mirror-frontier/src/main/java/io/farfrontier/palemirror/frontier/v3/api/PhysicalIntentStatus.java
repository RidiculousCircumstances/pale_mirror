package io.farfrontier.palemirror.frontier.v3.api;

/** Durable lifecycle; a recovered nonterminal intent is inspected, never blindly replayed. */
public enum PhysicalIntentStatus {
    PREPARED,
    RUNNING,
    CONFIRMED,
    UNKNOWN_AFTER_RESTART;

    public int wireTag() {
        return switch (this) {
            case PREPARED -> 0;
            case RUNNING -> 1;
            case CONFIRMED -> 2;
            case UNKNOWN_AFTER_RESTART -> 3;
        };
    }
}
