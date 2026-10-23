package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.Supplier;

/** Bounded derived views for independent subjects. Switching subjects must not discard their inputs. */
public final class ImmutableKeyedInputView<K, V> {
    private final int capacity;
    private final ImmutableInputView<Map<K, ImmutableInputView<V>>> generations = new ImmutableInputView<>();
    public ImmutableKeyedInputView(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("positive view capacity required");
        this.capacity = capacity;
    }
    public V get(Object generation, K key, List<Object> inputs, Supplier<V> compute) {
        Objects.requireNonNull(generation); Objects.requireNonNull(key); Objects.requireNonNull(compute);
        var views = generations.get(List.of(generation), () -> new LinkedHashMap<>(16, 0.75f, true));
        var view = views.get(key);
        if (view == null) {
            if (views.size() >= capacity) views.remove(views.keySet().iterator().next());
            view = new ImmutableInputView<>(); views.put(key, view);
        }
        return view.get(inputs, compute);
    }
}
