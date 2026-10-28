package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.BiFunction;

/** One computing-thread view of an explicit bounded producer registry. Live rows
 * never evict one another; removed rows and previous epochs are released. */
final class ImmutableRegistryView<K, I, V> {
    private record Entry<I, V>(I input, V value) { }
    private record Working<K, I, V>(Object generation, Map<K, Entry<I, V>> entries, Map<K, V> snapshot) { }
    private final int maximumEntries;
    private final ThreadLocal<Working<K, I, V>> current = new ThreadLocal<>();
    ImmutableRegistryView(int maximumEntries) {
        if (maximumEntries < 1) throw new IllegalArgumentException("registry view requires a declared positive bound");
        this.maximumEntries = maximumEntries;
    }
    Map<K, V> project(Object generation, Map<K, I> inputs, BiFunction<K, I, V> compute) {
        return project(generation, inputs, (before, after) -> before == after, compute);
    }
    Map<K, V> project(Object generation, Map<K, I> inputs, java.util.function.BiPredicate<I, I> unchanged,
                      BiFunction<K, I, V> compute) {
        Objects.requireNonNull(generation); Objects.requireNonNull(unchanged); Objects.requireNonNull(compute);
        if (inputs.size() > maximumEntries) throw new IllegalArgumentException("derived registry exceeds its declared producer bound");
        var working = current.get();
        boolean sameEpoch = working != null && working.generation() == generation;
        var previous = sameEpoch ? working.entries() : Map.<K, Entry<I, V>>of();
        Map<K, Entry<I, V>> next = null;
        if (previous.keySet().stream().anyMatch(key -> !inputs.containsKey(key))) {
            next = new HashMap<>(previous); next.keySet().retainAll(inputs.keySet());
        }
        for (var entry : inputs.entrySet()) {
            var key = Objects.requireNonNull(entry.getKey()); var input = Objects.requireNonNull(entry.getValue());
            var prior = previous.get(key);
            if (prior == null || !unchanged.test(prior.input(), input)) {
                var value = Objects.requireNonNull(compute.apply(key, input));
                if (next == null) next = new HashMap<>(previous);
                next.put(key, new Entry<>(input, value));
            }
        }
        if (next == null && sameEpoch) return working.snapshot();
        var accepted = next == null ? Map.<K, Entry<I, V>>of() : Collections.unmodifiableMap(next);
        var snapshot = new HashMap<K, V>(); accepted.forEach((key, value) -> snapshot.put(key, value.value()));
        var result = Collections.unmodifiableMap(snapshot);
        current.set(new Working<>(generation, accepted, result));
        return result;
    }
}
