package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.Supplier;

/** Bounded derived views for independent subjects. Switching subjects must not discard their inputs. */
public final class ImmutableKeyedInputView<K, V> {
    private final int capacity;
    private final ImmutableInputView<Map<K, ImmutableInputView.Entry<V>>> generations = new ImmutableInputView<>();
    public ImmutableKeyedInputView(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("positive view capacity required");
        this.capacity = capacity;
    }
    public V get(Object generation, K key, List<Object> inputs, Supplier<V> compute) {
        return get(generation, key, inputs, compute, false);
    }
    /** Producer-owned immutable publication: update evidence, retain equal value identity. */
    public V getPublished(Object generation, K key, List<Object> inputs, Supplier<V> compute) {
        return get(generation, key, inputs, compute, true);
    }
    private V get(Object generation, K key, List<Object> inputs, Supplier<V> compute, boolean publish) {
        Objects.requireNonNull(generation); Objects.requireNonNull(key); Objects.requireNonNull(compute);
        var views = generations.get(List.of(generation), () -> new LinkedHashMap<>(16, 0.75f, true));
        var view = views.get(key);
        if (view == null) {
            if (views.size() >= capacity) views.remove(views.keySet().iterator().next());
            view = new ImmutableInputView.Entry<>(); views.put(key, view);
        }
        var entry = view;
        // The directory is already isolated per computing thread. Rows are plain
        // values: generation/eviction cannot manufacture ThreadLocal keys.
        return ImmutablePlanningScope.get(entry, inputs, () -> entry.get(inputs, compute, publish));
    }
}
