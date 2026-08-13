package io.farfrontier.palemirror.domain;

import java.util.List;
import java.util.Objects;

/** Immutable geometry shared by journeys and physical transport contracts. */
public record WorldPath(String id, String policyVersion, WorldObjectId originSiteId,
                        WorldObjectId destinationSiteId, List<WorldPathNode> nodes) {
    public WorldPath {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("World path id is required");
        if (policyVersion == null || policyVersion.isBlank()) throw new IllegalArgumentException("World path version is required");
        Objects.requireNonNull(originSiteId, "originSiteId");
        Objects.requireNonNull(destinationSiteId, "destinationSiteId");
        nodes = List.copyOf(nodes);
        if (nodes.size() < 2) throw new IllegalArgumentException("World path needs at least two nodes");
        String dimension = nodes.getFirst().dimensionId();
        if (nodes.stream().anyMatch(node -> !Objects.equals(dimension, node.dimensionId()))) {
            throw new IllegalArgumentException("A v39 WorldPath cannot cross dimensions");
        }
    }
}
