package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Set;
import java.util.stream.Collectors;

/** Future land use, not plant state or a permission to mutate blocks. */
public record FieldSowingPlan(long revision, Set<ResourceFieldLayout.CellId> cells) {
    public FieldSowingPlan {
        cells = Set.copyOf(cells);
        if (revision < 1 || cells.size() > ResourceFieldLayout.MAX_CELLS)
            throw new IllegalArgumentException("invalid bounded sowing plan");
    }
    public static FieldSowingPlan initial(ResourceFieldLayout layout) {
        return new FieldSowingPlan(1, layout.cells().stream().map(ResourceFieldLayout.Cell::id).collect(Collectors.toSet()));
    }
    public void validate(ResourceFieldLayout layout) {
        cells.forEach(layout::requireCell);
    }
    public FieldSowingPlan revise(Set<ResourceFieldLayout.CellId> selected) {
        return cells.equals(selected) ? this : new FieldSowingPlan(Math.addExact(revision, 1), selected);
    }
}
