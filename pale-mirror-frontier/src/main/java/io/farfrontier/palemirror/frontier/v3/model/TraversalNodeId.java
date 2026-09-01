package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Local stable node identity inside one {@link TraversalTopology}. */
public record TraversalNodeId(String value) {
    public TraversalNodeId {
        Objects.requireNonNull(value, "traversal node id");
        if (value.isBlank() || value.length() > 80) throw new IllegalArgumentException("traversal node id is invalid");
    }
}
