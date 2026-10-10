package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Set;

/** Owner-retained admission frontier, not completion, geometry or a resource account. */
public record WorkAreaDevelopment(long revision, Set<Long> openedCells) {
    public static final int MAX_CELLS = 8_192;
    public WorkAreaDevelopment {
        openedCells = Set.copyOf(openedCells);
        if (revision < 1 || openedCells.isEmpty() || openedCells.size() > MAX_CELLS
                || openedCells.stream().anyMatch(id -> id < 1))
            throw new IllegalArgumentException("invalid bounded work-area development");
    }
    public WorkAreaDevelopment extend(long expectedRevision, Set<Long> additions) {
        if (revision != expectedRevision || additions.isEmpty() || additions.stream().anyMatch(openedCells::contains))
            throw new IllegalArgumentException("stale or repeated work-area development");
        var next = new java.util.HashSet<>(openedCells); next.addAll(additions);
        return new WorkAreaDevelopment(Math.addExact(revision, 1), next);
    }
}
