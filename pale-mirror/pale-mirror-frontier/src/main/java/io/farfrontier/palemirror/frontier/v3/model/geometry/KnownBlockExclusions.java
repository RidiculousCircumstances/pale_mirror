package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.*;

/** Known baseline invalidations. A foreign mutation grants no right to mine or restore its replacement. */
public record KnownBlockExclusions(Set<BlockPosition> positions) {
    public static final int MAX_POSITIONS = 32_768;
    public KnownBlockExclusions {
        positions = AppendOnlyPositions.copyOf(positions);
        if (positions.size() > MAX_POSITIONS) throw new IllegalArgumentException("known geometry invalidation capacity exhausted");
    }
    public static KnownBlockExclusions empty() { return new KnownBlockExclusions(Set.of()); }
    public KnownBlockExclusions invalidate(BlockPosition position) {
        Objects.requireNonNull(position);
        if (positions.contains(position)) return this;
        if (positions.size() == MAX_POSITIONS) throw new IllegalArgumentException("known geometry invalidation capacity exhausted");
        return new KnownBlockExclusions(((AppendOnlyPositions) positions).including(position));
    }

    /** Exact bounded append proof, not a guess from set size, coordinates or a world revision. */
    public Optional<Set<BlockPosition>> additionsSince(KnownBlockExclusions prior) {
        var current = (AppendOnlyPositions) positions;
        var previous = (AppendOnlyPositions) Objects.requireNonNull(prior).positions;
        if (current.base != previous.base || !current.additions.containsAll(previous.additions)) return Optional.empty();
        var added = new HashSet<>(current.additions); added.removeAll(previous.additions);
        return Optional.of(Set.copyOf(added));
    }

    /** Canonical immutable set, with bounded copy-on-write work and no chain of retired snapshots.
     * Persistence still writes the ordinary complete set in its existing stable wire grammar. */
    private static final class AppendOnlyPositions extends AbstractSet<BlockPosition> {
        private static final int MAX_ADDITIONS = 64;
        private final Set<BlockPosition> base;
        private final Set<BlockPosition> additions;
        private AppendOnlyPositions(Set<BlockPosition> base, Set<BlockPosition> additions) {
            this.base = base; this.additions = additions;
        }
        static AppendOnlyPositions copyOf(Set<BlockPosition> positions) {
            Objects.requireNonNull(positions);
            return positions instanceof AppendOnlyPositions immutable ? immutable
                    : new AppendOnlyPositions(Set.copyOf(positions), Set.of());
        }
        AppendOnlyPositions including(BlockPosition position) {
            if (additions.size() == MAX_ADDITIONS) {
                var snapshot = new HashSet<>(base); snapshot.addAll(additions);
                return new AppendOnlyPositions(Set.copyOf(snapshot), Set.of(position));
            }
            var next = new HashSet<>(additions); next.add(position);
            return new AppendOnlyPositions(base, Set.copyOf(next));
        }
        @Override public int size() { return base.size() + additions.size(); }
        @Override public boolean contains(Object position) { return additions.contains(position) || base.contains(position); }
        @Override public Iterator<BlockPosition> iterator() {
            var baseline = base.iterator(); var appended = additions.iterator();
            return new Iterator<>() {
                @Override public boolean hasNext() { return baseline.hasNext() || appended.hasNext(); }
                @Override public BlockPosition next() { return baseline.hasNext() ? baseline.next() : appended.next(); }
            };
        }
    }
}
