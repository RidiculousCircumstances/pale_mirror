package io.farfrontier.palemirror.frontier.v3.model.forestry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;

/** Spatial storage address only; membership never grants a tree owner or work permission. */
public record ForestRegion(int x, int z) implements Comparable<ForestRegion> {
    public static ForestRegion containing(BlockPosition position) { return new ForestRegion(position.x() >> 4, position.z() >> 4); }
    @Override public int compareTo(ForestRegion other) {
        int result = Integer.compare(x, other.x); return result == 0 ? Integer.compare(z, other.z) : result;
    }
}
