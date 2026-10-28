package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;
import java.lang.ref.WeakReference;

/** Immutable block facts with structural sharing and exact, cached regional fingerprints. */
public final class RegionalBlockMap<V> extends AbstractMap<BlockPosition, V> {
    private static final class Part<V> {
        final Map<BlockPosition, V> values;
        final int hash;
        // Exact equality of immutable parts remains true forever. Retain only a
        // weak peer: comparisons between independent worker/owner snapshots must
        // not rescan every block, or keep old geometry alive through cache chains.
        private volatile WeakReference<Part<?>> equalPeer;
        Part(Map<BlockPosition, V> values) {
            this.values = Collections.unmodifiableMap(new HashMap<>(values)); hash = values.hashCode();
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Part<?> part) || hash != part.hash) return false;
            var known = equalPeer;
            if (known != null && known.get() == part) return true;
            if (!values.equals(part.values)) return false;
            equalPeer = new WeakReference<>(part);
            part.equalPeer = new WeakReference<>(this);
            return true;
        }
    }
    private final ImmutableRegionMap<Part<V>> parts;
    private final int size, hash;
    private RegionalBlockMap(Map<Region, Part<V>> parts) {
        this.parts = ImmutableRegionMap.copyOf(parts);
        size = parts.values().stream().mapToInt(part -> part.values.size()).sum();
        hash = parts.values().stream().mapToInt(Part::hashCode).sum();
    }
    private RegionalBlockMap(ImmutableRegionMap<Part<V>> parts, int size, int hash) {
        this.parts = parts; this.size = size; this.hash = hash;
    }
    public static <V> RegionalBlockMap<V> copyOf(Map<BlockPosition, V> source) {
        if (source instanceof RegionalBlockMap<V> regional) return regional;
        return new RegionalBlockMap<V>(Map.of()).with(source);
    }
    public Map<BlockPosition, V> in(Region region) {
        var part = parts.get(region); return part == null ? Map.of() : part.values;
    }
    /** Complete immutable regional facts; equality is exact and hashes are cached. */
    public Object token(Region region) { return parts.getOrDefault(region, emptyPart()); }
    private static final Part<?> EMPTY = new Part<>(Map.of());
    @SuppressWarnings("unchecked") private static <T> Part<T> emptyPart() { return (Part<T>) EMPTY; }
    public Set<Region> regions() { return parts.keySet(); }
    public Set<Region> changesFrom(RegionalBlockMap<?> previous) {
        return parts.changesFrom(previous.parts);
    }
    public RegionalBlockMap<V> with(Map<BlockPosition, V> changes) {
        var updates = new HashMap<Region, Map<BlockPosition, V>>();
        changes.forEach((position, value) -> {
            Objects.requireNonNull(position); Objects.requireNonNull(value);
            if (!value.equals(get(position))) updates.computeIfAbsent(Region.at(position), region -> new HashMap<>(in(region)))
                    .put(position, value);
        });
        return replace(updates);
    }
    /** Replace complete changed regions, including removal; preserve all other immutable parts. */
    public RegionalBlockMap<V> replace(Map<Region, Map<BlockPosition, V>> changes) {
        var next = new HashMap<Region, Optional<Part<V>>>();
        int count = size, fingerprint = hash;
        for (var entry : changes.entrySet()) {
            var region = Objects.requireNonNull(entry.getKey()); var values = entry.getValue();
            values.forEach((position, value) -> {
                Objects.requireNonNull(value);
                if (!Region.at(position).equals(region)) throw new IllegalArgumentException("foreign geometry region");
            });
            if (values.equals(in(region))) continue;
            var old = parts.getOrDefault(region, emptyPart());
            var replacement = values.isEmpty() ? RegionalBlockMap.<V>emptyPart() : new Part<>(values);
            count += replacement.values.size() - old.values.size(); fingerprint += replacement.hash - old.hash;
            next.put(region, values.isEmpty() ? Optional.empty() : Optional.of(replacement));
        }
        var updated = parts.with(next);
        return updated == parts ? this : new RegionalBlockMap<>(updated, count, fingerprint);
    }
    @Override public V get(Object key) { return key instanceof BlockPosition position ? in(Region.at(position)).get(position) : null; }
    @Override public boolean containsKey(Object key) { return get(key) != null; }
    @Override public int size() { return size; }
    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object other) {
        return this == other || (other instanceof RegionalBlockMap<?> regional
                ? size == regional.size && hash == regional.hash && parts.equals(regional.parts) : super.equals(other));
    }
    @Override public Set<Entry<BlockPosition, V>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return size; }
            @Override public Iterator<Entry<BlockPosition, V>> iterator() {
                return parts.values().stream().flatMap(part -> part.values.entrySet().stream()).iterator();
            }
        };
    }
}
