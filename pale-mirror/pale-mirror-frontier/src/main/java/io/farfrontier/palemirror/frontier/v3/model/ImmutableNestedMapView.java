package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.Function;

/** Disposable exact child lookup over declared immutable producer rows. No owner
 * inference or canonical state: callers still validate their nominal references. */
final class ImmutableNestedMapView<K, I, J, V> {
    private static final class Image<K, I, J, V> {
        final Object epoch;
        Map<K, I> source = Map.of();
        List<Object> basis;
        final Map<K, Map<J, V>> parts = new HashMap<>();
        final Map<J, K> producers = new HashMap<>();
        Map<J, V> snapshot = ImmutableLookupMap.copyOf(Map.of());
        Image(Object epoch) { this.epoch = epoch; }
    }
    private final int maximumRows, maximumChildren;
    private final ThreadLocal<Image<K, I, J, V>> current = new ThreadLocal<>();
    ImmutableNestedMapView(int maximumRows, int maximumChildren) {
        if (maximumRows < 1 || maximumChildren < 1) throw new IllegalArgumentException("positive nested registry bounds required");
        this.maximumRows = maximumRows; this.maximumChildren = maximumChildren;
    }
    Map<J, V> project(Object epoch, Map<K, I> source, Function<I, Map<J, V>> project) {
        Objects.requireNonNull(epoch); Objects.requireNonNull(source); Objects.requireNonNull(project);
        if (source.size() > maximumRows) throw new IllegalArgumentException("nested registry exceeds its producer bound");
        Map<K, I> input = source instanceof ImmutableMapImage<?, ?> ? source : ImmutableLookupMap.copyOf(source);
        if (!ImmutablePlanningScope.active()) return projectCurrent(epoch, input, project);
        var image = current.get();
        var basis = image != null && image.epoch == epoch && image.source == input && image.basis != null
                ? image.basis : List.of(epoch, input);
        return ImmutablePlanningScope.publication(this, basis, () -> projectCurrent(epoch, input, project));
    }
    private Map<J, V> projectCurrent(Object epoch, Map<K, I> input, Function<I, Map<J, V>> project) {
        var image = current.get();
        if (image == null || image.epoch != epoch) image = new Image<>(epoch);
        if (image.source == input) return image.snapshot;
        try {
            var changed = new LinkedHashSet<K>();
            ImmutableMapChanges.forEachIdentityChangedKey(image.source, input, changed::add);
            var next = new LinkedHashMap<K, Map<J, V>>();
            for (var key : changed) {
                var row = input.get(key);
                next.put(key, row == null ? Map.of() : ImmutableLookupMap.copyOf(project.apply(row)));
            }
            // Remove all changed contributions before adding: one atomic input may
            // transfer a child between producers without depending on iteration order.
            var values = image.snapshot;
            for (var key : changed) for (var child : image.parts.getOrDefault(key, Map.of()).keySet()) {
                image.producers.remove(child);
                if (!next.get(key).containsKey(child)) values = ImmutableLookupMap.withoutEntry(values, child);
            }
            for (var entry : next.entrySet()) {
                for (var child : entry.getValue().entrySet()) {
                    if (image.producers.putIfAbsent(child.getKey(), entry.getKey()) != null)
                        throw new IllegalArgumentException("duplicate nested registry child identity");
                    if (values.get(child.getKey()) != child.getValue())
                        values = ImmutableLookupMap.withEntry(values, child.getKey(), child.getValue());
                }
                if (entry.getValue().isEmpty()) image.parts.remove(entry.getKey()); else image.parts.put(entry.getKey(), entry.getValue());
            }
            if (values.size() > maximumChildren) throw new IllegalArgumentException("nested registry exceeds its child bound");
            image.source = input; image.basis = List.of(epoch, input); image.snapshot = values; current.set(image);
            return values;
        } catch (RuntimeException | Error invalid) { current.remove(); throw invalid; }
    }
}
