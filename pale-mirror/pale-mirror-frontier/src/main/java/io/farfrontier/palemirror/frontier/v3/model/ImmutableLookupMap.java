package io.farfrontier.palemirror.frontier.v3.model;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Defensive immutable snapshot with hashed lookup and preserved source iteration order.
 * Unlike Map.copyOf's compact linear-probe layout, clustered domain IDs do not
 * turn ordinary register lookups into long contiguous probe walks. This is a
 * collection representation, never a second domain index or mutation authority. */
public final class ImmutableLookupMap<K, V> extends AbstractMap<K, V> {
    private final Map<K, V> values;

    private ImmutableLookupMap(Map<? extends K, ? extends V> source) {
        var copy = new LinkedHashMap<K, V>();
        source.forEach((key, value) -> copy.put(Objects.requireNonNull(key, "map key"),
                Objects.requireNonNull(value, "map value")));
        values = Collections.unmodifiableMap(copy);
    }

    public static <K, V> Map<K, V> copyOf(Map<? extends K, ? extends V> source) {
        Objects.requireNonNull(source, "immutable lookup source");
        if (source instanceof ImmutableLookupMap<?, ?>) {
            @SuppressWarnings("unchecked") Map<K, V> retained = (Map<K, V>) source;
            return retained;
        }
        return new ImmutableLookupMap<>(source);
    }

    @Override public V get(Object key) { return values.get(Objects.requireNonNull(key, "map key")); }
    @Override public boolean containsKey(Object key) { return values.containsKey(Objects.requireNonNull(key, "map key")); }
    @Override public boolean containsValue(Object value) { return values.containsValue(Objects.requireNonNull(value, "map value")); }
    @Override public Set<Entry<K, V>> entrySet() { return values.entrySet(); }
    @Override public Set<K> keySet() { return values.keySet(); }
    @Override public Collection<V> values() { return values.values(); }
    @Override public int size() { return values.size(); }
    @Override public V put(K key, V value) { return values.put(key, value); }
    @Override public V remove(Object key) { return values.remove(key); }
    @Override public void clear() { values.clear(); }
    @Override public void putAll(Map<? extends K, ? extends V> source) { values.putAll(source); }
    @Override public V putIfAbsent(K key, V value) { return values.putIfAbsent(key, value); }
    @Override public boolean remove(Object key, Object value) { return values.remove(key, value); }
    @Override public V replace(K key, V value) { return values.replace(key, value); }
    @Override public boolean replace(K key, V previous, V next) { return values.replace(key, previous, next); }
    @Override public void replaceAll(java.util.function.BiFunction<? super K, ? super V, ? extends V> operation) { values.replaceAll(operation); }
    @Override public V computeIfAbsent(K key, java.util.function.Function<? super K, ? extends V> operation) { return values.computeIfAbsent(key, operation); }
    @Override public V computeIfPresent(K key, java.util.function.BiFunction<? super K, ? super V, ? extends V> operation) { return values.computeIfPresent(key, operation); }
    @Override public V compute(K key, java.util.function.BiFunction<? super K, ? super V, ? extends V> operation) { return values.compute(key, operation); }
    @Override public V merge(K key, V value, java.util.function.BiFunction<? super V, ? super V, ? extends V> operation) { return values.merge(key, value, operation); }
}
