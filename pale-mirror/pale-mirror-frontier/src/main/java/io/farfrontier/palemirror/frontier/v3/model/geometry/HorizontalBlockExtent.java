package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;

/** Exact inclusive extrema of immutable block declarations; no occupancy,
 * clearance or ownership permission is implied by a bounding rectangle. */
public record HorizontalBlockExtent(int minX, int minZ, int maxX, int maxZ) {
    public HorizontalBlockExtent {
        if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("empty block extent");
    }
    public static HorizontalBlockExtent enclosing(Iterable<? extends Iterable<BlockPosition>> parts) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean present = false;
        for (var part : parts) for (var position : part) {
            present = true;
            minX = Math.min(minX, position.x()); minZ = Math.min(minZ, position.z());
            maxX = Math.max(maxX, position.x()); maxZ = Math.max(maxZ, position.z());
        }
        if (!present) throw new IllegalArgumentException("block extent needs an explicit nonempty declaration");
        return new HorizontalBlockExtent(minX, minZ, maxX, maxZ);
    }
    public boolean within(WorldBounds bounds) {
        return minX >= bounds.minX() && minZ >= bounds.minZ()
                && maxX < bounds.maxXExclusive() && maxZ < bounds.maxZExclusive();
    }
}
