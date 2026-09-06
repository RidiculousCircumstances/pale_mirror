package io.farfrontier.palemirror.domain;

/** Source-neutral waypoint. Coordinates are data, not a Minecraft dependency. */
public record WorldPathNode(String id, String dimensionId, int x, int y, int z, boolean safeCheckpoint) {
    public WorldPathNode {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Path node id is required");
        if (dimensionId == null || dimensionId.isBlank()) throw new IllegalArgumentException("Path node dimension is required");
    }
}
