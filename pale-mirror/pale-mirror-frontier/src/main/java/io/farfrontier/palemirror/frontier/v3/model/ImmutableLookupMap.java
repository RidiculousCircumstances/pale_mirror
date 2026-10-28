package io.farfrontier.palemirror.frontier.v3.model;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.lang.ref.WeakReference;

/** Defensive immutable snapshot with hashed lookup and preserved source iteration order.
 * Unlike Map.copyOf's compact linear-probe layout, clustered domain IDs do not
 * turn ordinary register lookups into long contiguous probe walks. This is a
 * collection representation, never a second domain index or mutation authority. */
public final class ImmutableLookupMap<K, V> extends AbstractMap<K, V> implements ImmutableMapImage<K, V> {
    private final Map<K, V> values;
    private record DirectChange<K>(WeakReference<Map<?, ?>> predecessor, Set<K> keys) { }
    private final Optional<DirectChange<K>> change;

    private ImmutableLookupMap(Map<? extends K, ? extends V> source) {
        values = Collections.unmodifiableMap(snapshot(source)); change = Optional.empty();
    }

    private ImmutableLookupMap(Map<? extends K, ? extends V> source, K key, V value) {
        LinkedHashMap<K, V> copy = snapshot(source);
        copy.put(Objects.requireNonNull(key, "map key"), Objects.requireNonNull(value, "map value"));
        values = Collections.unmodifiableMap(copy); change = capture(source, key);
    }
    private ImmutableLookupMap(Map<? extends K, ? extends V> source, K key) {
        LinkedHashMap<K, V> copy = snapshot(source); copy.remove(Objects.requireNonNull(key, "map key"));
        values = Collections.unmodifiableMap(copy); change = capture(source, key);
    }
    private static <K, V> LinkedHashMap<K, V> snapshot(Map<? extends K, ? extends V> source) {
        var copy = new LinkedHashMap<K, V>();
        source.forEach((key, value) -> copy.put(Objects.requireNonNull(key, "map key"), Objects.requireNonNull(value, "map value")));
        return copy;
    }
    private static <K> Optional<DirectChange<K>> capture(Map<?, ?> source, K key) {
        return source instanceof ImmutableMapImage<?, ?>
                ? Optional.of(new DirectChange<>(new WeakReference<>(source), Set.of(key))) : Optional.empty();
    }
    @Override public Optional<Set<K>> changedKeysFrom(Map<?, ?> predecessor) {
        Objects.requireNonNull(predecessor, "immutable map predecessor");
        return change.filter(value -> value.predecessor().get() == predecessor).map(DirectChange::keys);
    }
    /** One defensive copy for an immutable replacement; no parent chain or escaped mutable draft. */
    public static <K, V> Map<K, V> withEntry(Map<? extends K, ? extends V> source, K key, V value) {
        return new ImmutableLookupMap<>(Objects.requireNonNull(source, "immutable lookup source"), key, value);
    }
    public static <K, V> Map<K, V> withoutEntry(Map<? extends K, ? extends V> source, K key) {
        return new ImmutableLookupMap<>(Objects.requireNonNull(source, "immutable lookup source"), key);
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
