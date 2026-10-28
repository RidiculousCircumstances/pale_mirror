package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact immutable registry iteration order, independent of row-value changes.
 * One computing-thread image; no parent-map chain or canonical authority. */
final class ImmutableKeyOrderView<K> {
    private record Image<K>(Object token, List<K> order) { }
    private final int maximumKeys;
    private final ThreadLocal<Image<K>> current = new ThreadLocal<>();
    ImmutableKeyOrderView(int maximumKeys) {
        if (maximumKeys < 1) throw new IllegalArgumentException("positive registry order bound required");
        this.maximumKeys = maximumKeys;
    }
    List<K> get(Map<K, ?> source) {
        Objects.requireNonNull(source);
        if (source.size() > maximumKeys) throw new IllegalArgumentException("registry order exceeds its producer bound");
        var prior = current.get();
        Object token = source instanceof ImmutableMapImage<?, ?> image ? image.keyOrderIdentity() : null;
        if (token != null && prior != null && prior.token() == token) return prior.order();
        var order = List.copyOf(source.keySet());
        if (prior != null && prior.order().equals(order)) order = prior.order();
        current.set(new Image<>(token, order));
        return order;
    }
}
