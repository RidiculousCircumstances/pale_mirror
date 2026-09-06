package io.farfrontier.palemirror.frontier.v3.model;

/** Finite horizontal frontier bounds. Maximum coordinates are exclusive. */
public record WorldBounds(int minX, int minZ, int width, int depth) {
    public WorldBounds {
        if (width <= 0 || depth <= 0) throw new IllegalArgumentException("world bounds must be positive");
    }

    public int maxXExclusive() { return Math.addExact(minX, width); }
    public int maxZExclusive() { return Math.addExact(minZ, depth); }
    public boolean contains(BlockPosition position) {
        return position.x() >= minX && position.x() < maxXExclusive()
                && position.z() >= minZ && position.z() < maxZExclusive();
    }
}
