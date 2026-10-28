package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/** One computing-thread view of an explicit bounded producer registry. Live rows
 * never evict one another; removed rows and previous epochs are released. */
final class ImmutableRegistryView<K, I, V> {
    private record Entry<I, V>(I input, V value) { }
    private record Working<K, I, V>(Object generation, Map<K, I> source, Map<K, Entry<I, V>> entries, Map<K, V> snapshot) { }
    private final int maximumEntries;
    private final ThreadLocal<Working<K, I, V>> current = new ThreadLocal<>();
    ImmutableRegistryView(int maximumEntries) {
        if (maximumEntries < 1) throw new IllegalArgumentException("registry view requires a declared positive bound");
        this.maximumEntries = maximumEntries;
    }
    Map<K, V> project(Object generation, Map<K, I> inputs, BiFunction<K, I, V> compute) {
        Objects.requireNonNull(generation); Objects.requireNonNull(inputs); Objects.requireNonNull(compute);
        var working = current.get(); var changes = Optional.<Set<K>>empty();
        if (working != null && working.generation() == generation && inputs instanceof ImmutableMapImage<?, ?> image) {
            if (working.source() == inputs) return working.snapshot();
            @SuppressWarnings("unchecked") var typed = (ImmutableMapImage<K, I>) image;
            changes = typed.changedKeysFrom(working.source());
        }
        return project(generation, inputs, (before, after) -> before == after, compute, changes);
    }
    Map<K, V> project(Object generation, Map<K, I> inputs, BiPredicate<I, I> unchanged, BiFunction<K, I, V> compute) {
        // An explicit comparison may deliberately require full reevaluation. Never
        // replace that owner's decision with a direct-change or same-source shortcut.
        return project(generation, inputs, unchanged, compute, Optional.empty());
    }
    private Map<K, V> project(Object generation, Map<K, I> inputs, BiPredicate<I, I> unchanged,
                              BiFunction<K, I, V> compute, Optional<Set<K>> changes) {
        Objects.requireNonNull(generation); Objects.requireNonNull(unchanged); Objects.requireNonNull(compute);
        if (inputs.size() > maximumEntries) throw new IllegalArgumentException("derived registry exceeds its declared producer bound");
        var working = current.get(); boolean sameEpoch = working != null && working.generation() == generation;
        var previous = sameEpoch ? working.entries() : Map.<K, Entry<I, V>>of();
        var update = new Update<K, I, V>(previous);
        if (sameEpoch && changes.isPresent()) {
            for (var key : changes.orElseThrow()) {
                if (inputs.containsKey(key)) update.accept(key, inputs.get(key), unchanged, compute);
                else update.remove(key);
            }
        } else {
            for (var key : previous.keySet()) if (!inputs.containsKey(key)) update.remove(key);
            inputs.forEach((key, input) -> update.accept(key, input, unchanged, compute));
        }
        var accepted = update.entries();
        Map<K, V> result;
        if (sameEpoch && !update.valuesChanged) result = working.snapshot();
        else {
            var snapshot = new HashMap<K, V>(); accepted.forEach((key, value) -> snapshot.put(key, value.value()));
            result = Collections.unmodifiableMap(snapshot);
        }
        // All computations finish before any trusted basis is replaced.
        current.set(new Working<>(generation, inputs, accepted, result));
        return result;
    }
    private static final class Update<K, I, V> {
        private final Map<K, Entry<I, V>> previous;
        private Map<K, Entry<I, V>> next;
        private boolean valuesChanged;
        private Update(Map<K, Entry<I, V>> previous) { this.previous = previous; }
        private void accept(K key, I input, BiPredicate<I, I> unchanged, BiFunction<K, I, V> compute) {
            Objects.requireNonNull(key); Objects.requireNonNull(input);
            var prior = previous.get(key);
            if (prior != null && unchanged.test(prior.input(), input)) return;
            var value = Objects.requireNonNull(compute.apply(key, input));
            if (next == null) next = new HashMap<>(previous);
            next.put(key, new Entry<>(input, value));
            if (prior == null || value != prior.value()) valuesChanged = true;
        }
        private void remove(K key) {
            if (!previous.containsKey(key)) return;
            if (next == null) next = new HashMap<>(previous);
            next.remove(key); valuesChanged = true;
        }
        private Map<K, Entry<I, V>> entries() {
            return next == null ? previous : Collections.unmodifiableMap(next);
        }
    }
}
