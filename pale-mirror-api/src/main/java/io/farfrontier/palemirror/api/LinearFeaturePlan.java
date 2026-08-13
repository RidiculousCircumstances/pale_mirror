package io.farfrontier.palemirror.api;

import java.util.List;
import java.util.Objects;

/** Chunk-sliceable authored line with an explicit gameplay purpose. */
public record LinearFeaturePlan(String id, LinearFeatureKind kind, List<VisualPoint> nodes,
                                int width, boolean walkable) {
    public LinearFeaturePlan {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        Objects.requireNonNull(kind, "kind");
        nodes = List.copyOf(nodes);
        if (nodes.size() < 2) throw new IllegalArgumentException("linear feature requires at least two nodes");
        if (width < 1 || width > 7) throw new IllegalArgumentException("linear feature width must be 1..7");
    }
}
