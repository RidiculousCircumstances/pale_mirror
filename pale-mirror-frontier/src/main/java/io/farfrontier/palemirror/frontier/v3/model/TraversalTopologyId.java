package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Stable identifier for one immutable provider-neutral traversal topology revision. */
public record TraversalTopologyId(String value) {
    public TraversalTopologyId {
        Objects.requireNonNull(value, "traversal topology id");
        if (value.isBlank() || value.length() > 160) throw new IllegalArgumentException("traversal topology id is invalid");
    }
}
