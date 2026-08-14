package io.farfrontier.palemirror.api;

import java.util.List;

/** Irregular union of bounded authored areas; the enclosing bounds are indexing only. */
public record ManagedAreaPlan(List<VisualBounds> areas) {
    public ManagedAreaPlan {
        areas = List.copyOf(areas);
        if (areas.isEmpty()) throw new IllegalArgumentException("managed area requires at least one component");
    }

    public boolean contains(int x, int z) {
        return areas.stream().anyMatch(area -> x >= area.min().x() && x <= area.max().x()
                && z >= area.min().z() && z <= area.max().z());
    }
}
