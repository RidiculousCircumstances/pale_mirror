package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TerrainColumn;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Immutable layered support directory. A regional token hashes once and compares exact contents. */
public final class RegionalSurfaceMap extends AbstractMap<TerrainColumn, List<SurfaceAnchor>> {
    private static final RegionalSurfaceMap EMPTY = new RegionalSurfaceMap(Map.of());
    private static final class Columns extends ImmutableRegionalValue<Map<TerrainColumn, List<SurfaceAnchor>>> {
        Columns(Map<TerrainColumn, List<SurfaceAnchor>> values) {
            super(copy(values));
        }
        private static Map<TerrainColumn, List<SurfaceAnchor>> copy(Map<TerrainColumn, List<SurfaceAnchor>> values) {
            var copy = new HashMap<TerrainColumn, List<SurfaceAnchor>>();
            values.forEach((column, levels) -> {
                if (levels.isEmpty() || levels.stream().anyMatch(surface -> surface.x() != column.x() || surface.z() != column.z()))
                    throw new IllegalArgumentException("support contribution differs from its column");
                copy.put(column, List.copyOf(levels));
            });
            return Collections.unmodifiableMap(copy);
        }
    }
    private static final Columns EMPTY_COLUMNS = new Columns(Map.of());
    private final ImmutableRegionMap<Columns> regions;
    private final int size, hash;
    private RegionalSurfaceMap(Map<Region, Columns> regions) {
        this.regions = ImmutableRegionMap.copyOf(regions);
        size = regions.values().stream().mapToInt(columns -> columns.values.size()).sum();
        hash = regions.values().stream().mapToInt(Columns::hashCode).sum();
    }
    private RegionalSurfaceMap(ImmutableRegionMap<Columns> regions, int size, int hash) {
        this.regions = regions; this.size = size; this.hash = hash;
    }
    public static RegionalSurfaceMap copyOf(Map<TerrainColumn, List<SurfaceAnchor>> values) {
        if (values instanceof RegionalSurfaceMap indexed) return indexed;
        var chunks = new HashMap<Region, Map<TerrainColumn, List<SurfaceAnchor>>>();
        values.forEach((column, levels) -> chunks.computeIfAbsent(region(column), ignored -> new HashMap<>()).put(column, levels));
        return EMPTY.replace(chunks);
    }
    public static Region region(TerrainColumn column) { return new Region(column.x() >> 4, column.z() >> 4); }
    public Set<Region> regions() { return regions.keySet(); }
    public Object token(Region region) { return regions.getOrDefault(region, EMPTY_COLUMNS); }
    public Map<TerrainColumn, List<SurfaceAnchor>> in(Region region) { return regions.getOrDefault(region, EMPTY_COLUMNS).values; }
    @Override public int size() { return size; }
    @Override public int hashCode() { return hash; }
    @Override public List<SurfaceAnchor> get(Object column) {
        if (!(column instanceof TerrainColumn key)) return null;
        var columns = regions.get(key.x() >> 4, key.z() >> 4);
        return columns == null ? null : columns.values.get(key);
    }
    @Override public boolean containsKey(Object column) { return get(column) != null; }
    @Override public Set<Entry<TerrainColumn, List<SurfaceAnchor>>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return size; }
            @Override public Iterator<Entry<TerrainColumn, List<SurfaceAnchor>>> iterator() {
                return regions.values().stream().flatMap(columns -> columns.values.entrySet().stream()).iterator();
            }
        };
    }
    public Set<Region> changesFrom(RegionalSurfaceMap previous) {
        return regions.changesFrom(previous.regions);
    }
    public RegionalSurfaceMap replace(Map<Region, Map<TerrainColumn, List<SurfaceAnchor>>> updates) {
        var next = new HashMap<Region, Optional<Columns>>();
        int count = size, fingerprint = hash;
        for (var entry : updates.entrySet()) {
            if (in(entry.getKey()).equals(entry.getValue())) continue;
            if (entry.getValue().keySet().stream().anyMatch(column -> !region(column).equals(entry.getKey())))
                throw new IllegalArgumentException("support contribution crosses its region");
            var old = regions.getOrDefault(entry.getKey(), EMPTY_COLUMNS);
            var replacement = entry.getValue().isEmpty() ? EMPTY_COLUMNS : new Columns(entry.getValue());
            count += replacement.values.size() - old.values.size(); fingerprint += replacement.hash - old.hash;
            next.put(entry.getKey(), replacement == EMPTY_COLUMNS ? Optional.empty() : Optional.of(replacement));
        }
        var updated = regions.with(next);
        return updated == regions ? this : new RegionalSurfaceMap(updated, count, fingerprint);
    }
}
