package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.Objects;

/** Compact inclusive three-dimensional block bounds, not an allocated voxel array. */
public record BlockVolume(BlockPosition min, BlockPosition max) {
    public BlockVolume {
        Objects.requireNonNull(min); Objects.requireNonNull(max);
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()
                || (long) max.x() - min.x() > 4095 || (long) max.y() - min.y() > 4095 || (long) max.z() - min.z() > 4095)
            throw new IllegalArgumentException("invalid bounded block volume");
    }
    public boolean contains(BlockPosition position) {
        return position.x() >= min.x() && position.x() <= max.x() && position.y() >= min.y() && position.y() <= max.y()
                && position.z() >= min.z() && position.z() <= max.z();
    }
    public boolean overlaps(BlockVolume other) {
        return min.x() <= other.max.x() && max.x() >= other.min.x() && min.y() <= other.max.y() && max.y() >= other.min.y()
                && min.z() <= other.max.z() && max.z() >= other.min.z();
    }
    public long blocks() { return ((long) max.x() - min.x() + 1) * ((long) max.y() - min.y() + 1) * ((long) max.z() - min.z() + 1); }
}
