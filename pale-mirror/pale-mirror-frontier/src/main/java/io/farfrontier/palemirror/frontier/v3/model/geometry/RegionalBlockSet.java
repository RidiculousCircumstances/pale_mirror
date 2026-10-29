package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Immutable spatial set. Local overlays copy only touched regions; snapshots never retain parents. */
public final class RegionalBlockSet extends AbstractSet<BlockPosition> {
    private static final RegionalBlockSet EMPTY = new RegionalBlockSet(Map.of());
    private final ImmutableRegionMap<Blocks> regions;
    private final int size;
    private final int hash;

    /** Exact immutable contents with an O(1) hash; hashes alone never authorize equality. */
    private static final class Blocks extends ImmutableRegionalValue<Set<BlockPosition>> {
        Blocks(Set<BlockPosition> values) {
            super(Collections.unmodifiableSet(new HashSet<>(values)));
        }
    }
    private RegionalBlockSet(Map<Region, Blocks> regions) {
        this.regions = ImmutableRegionMap.copyOf(regions);
        this.size = regions.values().stream().mapToInt(blocks -> blocks.values.size()).sum();
        this.hash = regions.values().stream().mapToInt(Blocks::hashCode).sum();
    }
    private RegionalBlockSet(ImmutableRegionMap<Blocks> regions, int size, int hash) {
        this.regions = regions; this.size = size; this.hash = hash;
    }
    private RegionalBlockSet withParts(Map<Region, Optional<Blocks>> updates) {
        int count = size, fingerprint = hash;
        for (var entry : updates.entrySet()) {
            var old = regions.getOrDefault(entry.getKey(), EMPTY_BLOCKS);
            var next = entry.getValue().orElse(EMPTY_BLOCKS);
            count += next.values.size() - old.values.size(); fingerprint += next.hash - old.hash;
        }
        var next = regions.with(updates);
        return next == regions ? this : new RegionalBlockSet(next, count, fingerprint);
    }
    public static RegionalBlockSet copyOf(Set<BlockPosition> values) {
        return values instanceof RegionalBlockSet indexed ? indexed : EMPTY.overlay(Set.of(), values);
    }
    /** Compose immutable contributions without copying disjoint regional contents. */
    public static RegionalBlockSet union(Collection<RegionalBlockSet> sources) {
        var parts = new HashMap<Region, Blocks>();
        for (var source : sources) source.regions.forEach((region, blocks) -> parts.merge(region, blocks, RegionalBlockSet::join));
        return parts.isEmpty() ? EMPTY : new RegionalBlockSet(parts);
    }
    private static Blocks join(Blocks left, Blocks right) {
        if (left == right || left.values.size() >= right.values.size() && left.values.containsAll(right.values)) return left;
        if (right.values.size() >= left.values.size() && right.values.containsAll(left.values)) return right;
        var values = new HashSet<>(left.values); values.addAll(right.values); return new Blocks(values);
    }
    /** Replace only changed unions, sharing complete immutable producer parts.
     * A single contributor needs no block enumeration or second regional copy. */
    RegionalBlockSet replaceUnion(Set<Region> changed, Collection<RegionalBlockSet> sources) {
        if (changed.isEmpty()) return this;
        var updates = new HashMap<Region, Optional<Blocks>>();
        for (var region : changed) {
            Blocks combined = null;
            for (var source : sources) {
                var blocks = source.regions.get(region);
                if (blocks != null) combined = combined == null ? blocks : join(combined, blocks);
            }
            if (Objects.equals(regions.get(region), combined)) continue;
            updates.put(region, Optional.ofNullable(combined));
        }
        return withParts(updates);
    }
    public Set<Region> regions() { return regions.keySet(); }
    public Object token(Region region) { return regions.getOrDefault(region, EMPTY_BLOCKS); }
    private static final Blocks EMPTY_BLOCKS = new Blocks(Set.of());
    public Set<BlockPosition> in(Region region) { return regions.getOrDefault(region, EMPTY_BLOCKS).values; }
    @Override public int size() { return size; }
    @Override public int hashCode() { return hash; }
    @Override public boolean contains(Object value) {
        if (!(value instanceof BlockPosition position)) return false;
        var blocks = regions.get(position.x() >> 4, position.z() >> 4);
        return blocks != null && blocks.values.contains(position);
    }
    @Override public Iterator<BlockPosition> iterator() {
        return regions.values().stream().flatMap(blocks -> blocks.values.stream()).iterator();
    }
    public Set<Region> changesFrom(RegionalBlockSet previous) {
        return regions.changesFrom(previous.regions);
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
        var next = new HashMap<Region, Optional<Blocks>>();
        for (var entry : updates.entrySet()) {
            if (in(entry.getKey()).equals(entry.getValue())) continue;
            if (entry.getValue().stream().anyMatch(position -> !Region.at(position).equals(entry.getKey())))
                throw new IllegalArgumentException("block contribution crosses its declared region");
            next.put(entry.getKey(), entry.getValue().isEmpty() ? Optional.empty() : Optional.of(new Blocks(entry.getValue())));
        }
        return withParts(next);
    }
}
