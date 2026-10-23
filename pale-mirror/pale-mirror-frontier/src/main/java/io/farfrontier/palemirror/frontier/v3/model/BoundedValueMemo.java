package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.Supplier;

/** Disposable results of pure functions over complete immutable value inputs.
 * Equality is exact, never a hash-only validity proof, elapsed TTL or execution authority.
 * Each instance belongs to one typed producer and evicts least recently used inputs. */
public final class BoundedValueMemo<K, V> {
    private final int capacity;
    private final Map<K, V> values = new LinkedHashMap<>(16, 0.75f, true);
    public BoundedValueMemo(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("positive memo capacity required");
        this.capacity = capacity;
    }
    public synchronized V get(K input, Supplier<V> compute) {
        Objects.requireNonNull(input); Objects.requireNonNull(compute);
        V existing = values.get(input);
        if (existing != null) return existing;
        V result = Objects.requireNonNull(compute.get());
        if (values.size() >= capacity) values.remove(values.keySet().iterator().next());
        values.put(input, result);
        return result;
    }
}
