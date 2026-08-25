package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code AgentKind} vocabulary. */
public enum ReferenceAgentKind {
    SETTLEMENT("settlement"),
    COLONY("colony");

    private final String id;

    ReferenceAgentKind(String id) { this.id = id; }
    public String id() { return id; }
}
