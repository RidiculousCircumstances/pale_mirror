package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Immutable Python {@code AgentRef}. */
public record ReferenceAgentRef(ReferenceAgentKind kind, int id) {
    public ReferenceAgentRef { Objects.requireNonNull(kind, "kind"); }
    public String key() { return kind.id() + ":" + id; }
}
