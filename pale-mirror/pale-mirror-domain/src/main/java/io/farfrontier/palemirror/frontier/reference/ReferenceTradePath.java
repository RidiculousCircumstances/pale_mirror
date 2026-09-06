package io.farfrontier.palemirror.frontier.reference;

import java.util.List;
import java.util.Objects;

/** Shortest physical route and its generalized logistics cost. */
public record ReferenceTradePath(double logisticsCost, List<Integer> nodes, List<ReferenceRoute> edges) {
    public ReferenceTradePath {
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
    }
}
