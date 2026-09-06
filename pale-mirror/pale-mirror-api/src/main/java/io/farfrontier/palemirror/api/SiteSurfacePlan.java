package io.farfrontier.palemirror.api;

import java.util.List;

/** Persisted absolute surface datums; runtime materialization never re-queries a heightmap for these columns. */
public record SiteSurfacePlan(List<SiteSurfaceColumn> columns) {
    public SiteSurfacePlan {
        columns = List.copyOf(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("site surface plan requires columns");
        if (columns.stream().map(value -> (((long) value.x()) << 32) ^ (value.z() & 0xffffffffL))
                .distinct().count() != columns.size()) {
            throw new IllegalArgumentException("site surface columns must have exclusive coordinates");
        }
    }

    public SiteSurfaceColumn require(int x, int z) {
        return columns.stream().filter(value -> value.x() == x && value.z() == z).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unclaimed site surface " + x + "," + z));
    }
}
