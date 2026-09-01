package io.farfrontier.palemirror.frontier.v3.model;

/** Immutable horizontal column identity for a surveyed terrain support surface. */
public record TerrainColumn(int x, int z) {
    public static TerrainColumn at(BlockPosition position) { return new TerrainColumn(position.x(), position.z()); }
}
