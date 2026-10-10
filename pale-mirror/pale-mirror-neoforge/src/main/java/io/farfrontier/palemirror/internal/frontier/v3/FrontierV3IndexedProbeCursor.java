package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.*;
import java.util.function.LongPredicate;

/** Runtime-only fair reads of naturally available immutable slices; never an effect receipt. */
final class FrontierV3IndexedProbeCursor<T> {
    private Map<Long, List<T>> index;
    private final Map<Long, Integer> offsets = new HashMap<>();
    private int nextSlice;

    Iterable<T> probes(Map<Long, List<T>> current, LongPredicate available, int budget) {
        if (budget < 1) throw new IllegalArgumentException("probe budget must be positive");
        if (index != current) { index = current; offsets.clear(); nextSlice = 0; }
        var slices = current.entrySet().stream().filter(entry -> !entry.getValue().isEmpty()
                && available.test(entry.getKey())).toList();
        int count = Math.min(budget, slices.stream().mapToInt(entry -> entry.getValue().size()).sum());
        return () -> new Iterator<>() {
            private int remaining = count;
            @Override public boolean hasNext() { return remaining > 0; }
            @Override public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                var slice = slices.get(nextSlice % slices.size());
                nextSlice = (nextSlice + 1) % slices.size();
                int offset = offsets.getOrDefault(slice.getKey(), 0);
                T value = slice.getValue().get(offset);
                offsets.put(slice.getKey(), (offset + 1) % slice.getValue().size());
                remaining--;
                return value;
            }
        };
    }
}
