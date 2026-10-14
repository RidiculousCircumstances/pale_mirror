package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Immutable spatial set. Local overlays copy only touched regions; snapshots never retain parents. */
public final class RegionalBlockSet extends AbstractSet<BlockPosition> {
    private static final RegionalBlockSet EMPTY = new RegionalBlockSet(Map.of());
    private final Map<Region, Blocks> regions;
    private final int size;
    private final int hash;

    /** Exact immutable contents with an O(1) hash; hashes alone never authorize equality. */
    private static final class Blocks {
        private final Set<BlockPosition> values;
        private final int hash;
        Blocks(Set<BlockPosition> values) {
            this.values = Collections.unmodifiableSet(new HashSet<>(values)); this.hash = values.hashCode();
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Blocks blocks && hash == blocks.hash && values.equals(blocks.values);
        }
    }
    private RegionalBlockSet(Map<Region, Blocks> regions) {
        this.regions = Collections.unmodifiableMap(new HashMap<>(regions));
        this.size = regions.values().stream().mapToInt(blocks -> blocks.values.size()).sum();
        this.hash = regions.values().stream().mapToInt(Blocks::hashCode).sum();
    }
    public static RegionalBlockSet copyOf(Set<BlockPosition> values) {
        return values instanceof RegionalBlockSet indexed ? indexed : EMPTY.overlay(Set.of(), values);
    }
    public Set<Region> regions() { return regions.keySet(); }
    public Object token(Region region) { return regions.getOrDefault(region, EMPTY_BLOCKS); }
    private static final Blocks EMPTY_BLOCKS = new Blocks(Set.of());
    public Set<BlockPosition> in(Region region) { return regions.getOrDefault(region, EMPTY_BLOCKS).values; }
    @Override public int size() { return size; }
    @Override public int hashCode() { return hash; }
    @Override public boolean contains(Object value) {
        return value instanceof BlockPosition position && in(Region.at(position)).contains(position);
    }
    @Override public Iterator<BlockPosition> iterator() {
        return regions.values().stream().flatMap(blocks -> blocks.values.stream()).iterator();
    }
    public Set<Region> changesFrom(RegionalBlockSet previous) {
        if (this == previous) return Set.of();
        var changed = new HashSet<>(regions.keySet()); changed.addAll(previous.regions.keySet());
        changed.removeIf(region -> token(region).equals(previous.token(region)));
        return Set.copyOf(changed);
    }
    /** Removal precedes addition, so witnessed obstacles cannot be cleared by authored permissions. */
    public RegionalBlockSet overlay(Set<BlockPosition> removed, Set<BlockPosition> added) {
        var updates = new HashMap<Region, Set<BlockPosition>>();
        for (var position : removed) if (contains(position))
            updates.computeIfAbsent(Region.at(position), region -> new HashSet<>(in(region))).remove(position);
        for (var position : added) if (!contains(position) || removed.contains(position))
            updates.computeIfAbsent(Region.at(position), region -> new HashSet<>(in(region))).add(position);
        return replace(updates);
    }
    /** Replaces exact regional contributions, retaining equal regional objects. */
    public RegionalBlockSet replace(Map<Region, Set<BlockPosition>> updates) {
        Map<Region, Blocks> next = null;
        for (var entry : updates.entrySet()) {
            if (in(entry.getKey()).equals(entry.getValue())) continue;
            if (entry.getValue().stream().anyMatch(position -> !Region.at(position).equals(entry.getKey())))
                throw new IllegalArgumentException("block contribution crosses its declared region");
            if (next == null) next = new HashMap<>(regions);
            if (entry.getValue().isEmpty()) next.remove(entry.getKey());
            else next.put(entry.getKey(), new Blocks(entry.getValue()));
        }
        return next == null ? this : new RegionalBlockSet(next);
    }
}
