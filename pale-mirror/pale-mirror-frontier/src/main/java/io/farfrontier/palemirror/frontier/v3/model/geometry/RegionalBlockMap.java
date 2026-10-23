package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Immutable block facts with structural sharing and exact, cached regional fingerprints. */
public final class RegionalBlockMap<V> extends AbstractMap<BlockPosition, V> {
    private static final class Part<V> {
        final Map<BlockPosition, V> values;
        final int hash;
        Part(Map<BlockPosition, V> values) {
            this.values = Collections.unmodifiableMap(new HashMap<>(values)); hash = values.hashCode();
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Part<?> part && hash == part.hash && values.equals(part.values);
        }
    }
    private final Map<Region, Part<V>> parts;
    private final int size, hash;
    private RegionalBlockMap(Map<Region, Part<V>> parts) {
        this.parts = Collections.unmodifiableMap(new HashMap<>(parts));
        size = parts.values().stream().mapToInt(part -> part.values.size()).sum();
        hash = parts.values().stream().mapToInt(Part::hashCode).sum();
    }
    public static <V> RegionalBlockMap<V> copyOf(Map<BlockPosition, V> source) {
        if (source instanceof RegionalBlockMap<V> regional) return regional;
        return new RegionalBlockMap<V>(Map.of()).with(source);
    }
    public Map<BlockPosition, V> in(Region region) {
        var part = parts.get(region); return part == null ? Map.of() : part.values;
    }
    public Set<Region> regions() { return parts.keySet(); }
    public Set<Region> changesFrom(RegionalBlockMap<?> previous) {
        if (this == previous) return Set.of();
        var result = new HashSet<>(parts.keySet()); result.addAll(previous.parts.keySet());
        result.removeIf(region -> Objects.equals(parts.get(region), previous.parts.get(region)));
        return Set.copyOf(result);
    }
    public RegionalBlockMap<V> with(Map<BlockPosition, V> changes) {
        var updates = new HashMap<Region, Map<BlockPosition, V>>();
        changes.forEach((position, value) -> {
            Objects.requireNonNull(position); Objects.requireNonNull(value);
            if (!value.equals(get(position))) updates.computeIfAbsent(Region.at(position), region -> new HashMap<>(in(region)))
                    .put(position, value);
        });
        if (updates.isEmpty()) return this;
        var next = new HashMap<>(parts);
        updates.forEach((region, values) -> next.put(region, new Part<>(values)));
        return new RegionalBlockMap<>(next);
    }
    /** Replace complete changed regions, including removal; preserve all other immutable parts. */
    public RegionalBlockMap<V> replace(Map<Region, Map<BlockPosition, V>> changes) {
        Map<Region, Part<V>> next = null;
        for (var entry : changes.entrySet()) {
            var region = Objects.requireNonNull(entry.getKey()); var values = entry.getValue();
            values.forEach((position, value) -> {
                Objects.requireNonNull(value);
                if (!Region.at(position).equals(region)) throw new IllegalArgumentException("foreign geometry region");
            });
            if (values.equals(in(region))) continue;
            if (next == null) next = new HashMap<>(parts);
            if (values.isEmpty()) next.remove(region); else next.put(region, new Part<>(values));
        }
        return next == null ? this : new RegionalBlockMap<>(next);
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
