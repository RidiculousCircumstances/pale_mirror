package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.Supplier;

/** Disposable results of pure functions over complete immutable value inputs.
 * Equality is exact, never a hash-only validity proof, elapsed TTL or execution authority.
 * Each instance belongs to one typed producer. Ordinary views retain access
 * order; read-mostly facts use insertion-order eviction and lock-free hits. */
public final class BoundedValueMemo<K, V> {
    private final int capacity;
    private final Map<K, V> values;
    private final ArrayDeque<K> insertionOrder;
    public BoundedValueMemo(int capacity) { this(capacity, false); }
    /** Immutable high-volume facts: a hit does not write LRU links or acquire the
     * miss/eviction monitor. Capacity and exact key equality are unchanged. */
    public static <K, V> BoundedValueMemo<K, V> readMostly(int capacity) {
        return new BoundedValueMemo<>(capacity, true);
    }
    private BoundedValueMemo(int capacity, boolean readMostly) {
        if (capacity < 1) throw new IllegalArgumentException("positive memo capacity required");
        this.capacity = capacity;
        values = readMostly ? new java.util.concurrent.ConcurrentHashMap<>() : new LinkedHashMap<>(16, 0.75f, true);
        insertionOrder = readMostly ? new ArrayDeque<>() : null;
    }
    public V get(K input, Supplier<V> compute) {
        Objects.requireNonNull(input); Objects.requireNonNull(compute);
        if (insertionOrder != null) {
            V existing = values.get(input);
            if (existing != null) return existing;
        } else synchronized (values) {
            V existing = values.get(input);
            if (existing != null) return existing;
        }
        V result = Objects.requireNonNull(compute.get());
        synchronized (values) {
            V existing = values.get(input);
            if (existing != null) return existing;
            if (values.size() >= capacity) values.remove(insertionOrder == null
                    ? values.keySet().iterator().next() : insertionOrder.removeFirst());
            values.put(input, result);
            if (insertionOrder != null) insertionOrder.addLast(input);
            return result;
        }
    }
}
