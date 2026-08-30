package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Derived, bounded persistent priority index for deterministic infection-growth selection. */
final class FrontierInfectionFrontier {
    private final WorldBounds bounds;
    private final HeapNode candidates;
    private final int retainedEntries;
    private final InfectionChange change;
    /** Noncanonical memo of stale-head removal; it is a pure cache over immutable input. */
    private volatile HeapNode normalizedCandidates;

    private FrontierInfectionFrontier(WorldBounds bounds, HeapNode candidates, int retainedEntries, InfectionChange change) {
        this.bounds = bounds; this.candidates = candidates; this.retainedEntries = retainedEntries; this.change = change;
    }

    static FrontierInfectionFrontier compile(WorldBounds bounds, java.util.Map<InfectionCell, FixedRatio> infection) {
        Set<InfectionCell> exact = new HashSet<>();
        infection.keySet().forEach(source -> adjacent(source).stream().filter(cell -> inside(bounds, cell)).forEach(exact::add));
        HeapNode heap = null;
        for (InfectionCell cell : exact) heap = HeapNode.add(heap, new Priority(cell, raw(infection, cell)));
        return new FrontierInfectionFrontier(bounds, heap, exact.size(), null);
    }

    FrontierInfectionFrontier changed(java.util.Map<InfectionCell, FixedRatio> before, java.util.Map<InfectionCell, FixedRatio> after,
                                      InfectionCell changed) {
        long previousRaw = raw(before, changed), nextRaw = raw(after, changed);
        if (previousRaw == nextRaw) return this;
        HeapNode next = normalizedCandidates == null ? candidates : normalizedCandidates;
        int nextEntries = retainedEntries;
        if (previousRaw == 0L && nextRaw != 0L) {
            for (InfectionCell neighbor : adjacent(changed)) if (inside(bounds, neighbor)) {
                next = HeapNode.add(next, new Priority(neighbor, raw(after, neighbor))); nextEntries++;
            }
        }
        if (hasInfectedNeighbor(after, changed)) {
            next = HeapNode.add(next, new Priority(changed, nextRaw)); nextEntries++;
        }
        InfectionChange mutation = new InfectionChange(changed, previousRaw, nextRaw);
        FrontierInfectionFrontier changedIndex = new FrontierInfectionFrontier(bounds, next, nextEntries, mutation);
        return nextEntries <= maxEntries(bounds) ? changedIndex : compile(bounds, after).withChange(mutation);
    }

    Optional<InfectionCell> best(java.util.Map<InfectionCell, FixedRatio> infection) {
        HeapNode next = normalizedCandidates == null ? candidates : normalizedCandidates;
        while (next != null) {
            Priority priority = next.priority;
            if (raw(infection, priority.cell) == priority.intensity && hasInfectedNeighbor(infection, priority.cell)) {
                normalizedCandidates = next;
                return Optional.of(priority.cell);
            }
            next = HeapNode.removeRoot(next);
        }
        normalizedCandidates = null;
        return Optional.empty();
    }

    Optional<InfectionChange> change() { return Optional.ofNullable(change); }

    private FrontierInfectionFrontier withChange(InfectionChange replacement) {
        return new FrontierInfectionFrontier(bounds, candidates, retainedEntries, replacement);
    }

    private static int maxEntries(WorldBounds bounds) {
        return Math.max(1, Math.multiplyExact(Math.floorDiv(bounds.width() + 3, 4), Math.floorDiv(bounds.depth() + 3, 4)));
    }

    private static boolean hasInfectedNeighbor(java.util.Map<InfectionCell, FixedRatio> infection, InfectionCell cell) {
        return adjacent(cell).stream().anyMatch(infection::containsKey);
    }

    private static boolean inside(WorldBounds bounds, InfectionCell cell) { return bounds.contains(cell.originAtY(64)); }

    private static long raw(java.util.Map<InfectionCell, FixedRatio> infection, InfectionCell cell) {
        return infection.getOrDefault(cell, new FixedRatio(FixedScalar.ZERO)).value().raw();
    }

    private static java.util.List<InfectionCell> adjacent(InfectionCell cell) {
        return java.util.List.of(new InfectionCell(cell.x() + 1, cell.z()), new InfectionCell(cell.x(), cell.z() + 1),
                new InfectionCell(cell.x() - 1, cell.z()), new InfectionCell(cell.x(), cell.z() - 1));
    }

    record InfectionChange(InfectionCell cell, long previousRaw, long nextRaw) { }

    private record Priority(InfectionCell cell, long intensity) implements Comparable<Priority> {
        @Override public int compareTo(Priority other) {
            int byIntensity = Long.compare(intensity, other.intensity);
            if (byIntensity != 0) return byIntensity;
            int byX = Integer.compare(cell.x(), other.cell.x());
            return byX != 0 ? byX : Integer.compare(cell.z(), other.cell.z());
        }
    }

    /** Immutable leftist heap; each insert shares all unaffected priority branches. */
    private static final class HeapNode {
        private final Priority priority; private final HeapNode left; private final HeapNode right; private final int rank;
        private HeapNode(Priority priority, HeapNode left, HeapNode right) {
            this.priority = priority;
            if (rank(left) < rank(right)) { this.left = right; this.right = left; }
            else { this.left = left; this.right = right; }
            this.rank = rank(this.right) + 1;
        }
        private static HeapNode add(HeapNode root, Priority priority) { return merge(root, new HeapNode(priority, null, null)); }
        private static HeapNode removeRoot(HeapNode root) { return merge(root.left, root.right); }
        private static HeapNode merge(HeapNode first, HeapNode second) {
            if (first == null) return second; if (second == null) return first;
            if (first.priority.compareTo(second.priority) > 0) { HeapNode swap = first; first = second; second = swap; }
            return new HeapNode(first.priority, first.left, merge(first.right, second));
        }
        private static int rank(HeapNode value) { return value == null ? 0 : value.rank; }
    }
}
