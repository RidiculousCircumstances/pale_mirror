package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.*;

/** Positive completion evidence, compressed into disjoint issued-sequence intervals.
 * At most pending operations + 1 intervals survive, regardless of dead native bodies. */
final class FrontierV3RetiredOperationRanges {
    private final Map<Long, Long> stored;
    private final NavigableMap<Long, Long> index;
    FrontierV3RetiredOperationRanges(Map<Long, Long> stored, Collection<Long> pending, long next) {
        this.stored = stored; index = new TreeMap<>(stored);
        var coverage = new TreeMap<>(index);
        long previous = -1;
        for (var range : index.entrySet()) {
            if (range.getKey() < 1 || range.getValue() < range.getKey() || range.getValue() >= next
                    || range.getKey() <= previous + 1)
                throw new IllegalArgumentException("invalid or unmerged operation retirement range");
            previous = range.getValue();
        }
        for (long sequence : pending)
            if (coverage.putIfAbsent(sequence, sequence) != null)
                throw new IllegalArgumentException("pending operation intersects retired history");
        long expected = 1;
        for (var range : coverage.entrySet()) {
            if (range.getKey() != expected) throw new IllegalArgumentException("operation history has a gap or overlap");
            expected = Math.incrementExact(range.getValue());
        }
        if (expected != next) throw new IllegalArgumentException("operation history does not cover issued sequences");
    }
    boolean contains(long sequence) {
        var lower = index.floorEntry(sequence);
        return lower != null && lower.getValue() >= sequence;
    }
    void add(long sequence) {
        if (contains(sequence)) throw new IllegalArgumentException("operation already retired");
        long start = sequence, end = sequence;
        var lower = index.lowerEntry(sequence);
        var upper = index.higherEntry(sequence);
        if (lower != null && lower.getValue() == sequence - 1) {
            start = lower.getKey(); remove(lower.getKey());
        }
        if (upper != null && upper.getKey() == sequence + 1) {
            end = upper.getValue(); remove(upper.getKey());
        }
        index.put(start, end); stored.put(start, end);
    }
    private void remove(long start) { index.remove(start); stored.remove(start); }
}
