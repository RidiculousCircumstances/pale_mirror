package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;

import java.util.Objects;

/** Bounded local block volume, inclusive lower and exclusive upper coordinates. */
public record BuildingVolume(BlockPosition min, BlockPosition max) {
    public BuildingVolume {
        Objects.requireNonNull(min);
        Objects.requireNonNull(max);
        if (min.x() >= max.x()
                || min.y() >= max.y()
                || min.z() >= max.z()
                || (long) max.x() - min.x() > 512
                || (long) max.y() - min.y() > 512
                || (long) max.z() - min.z() > 512
                || ((long) max.x() - min.x())
                                * ((long) max.y() - min.y())
                                * ((long) max.z() - min.z())
                        > 262_144)
            throw new IllegalArgumentException("invalid bounded building volume");
    }

    public boolean contains(BlockPosition p) {
        return p.x() >= min.x()
                && p.x() < max.x()
                && p.y() >= min.y()
                && p.y() < max.y()
                && p.z() >= min.z()
                && p.z() < max.z();
    }

    public boolean contains(BuildingVolume other) {
        return contains(other.min()) && contains(other.max().offset(-1, -1, -1));
    }

    public boolean overlaps(BuildingVolume other) {
        return min.x() < other.max.x()
                && max.x() > other.min.x()
                && min.y() < other.max.y()
                && max.y() > other.min.y()
                && min.z() < other.max.z()
                && max.z() > other.min.z();
    }
}
