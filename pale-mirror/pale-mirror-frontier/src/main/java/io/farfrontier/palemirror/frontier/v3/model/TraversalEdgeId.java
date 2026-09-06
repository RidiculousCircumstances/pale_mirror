package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Local stable edge identity inside one {@link TraversalTopology}. */
public record TraversalEdgeId(String value) {
    public TraversalEdgeId {
        Objects.requireNonNull(value, "traversal edge id");
        if (value.isBlank() || value.length() > 80) throw new IllegalArgumentException("traversal edge id is invalid");
    }
}
