package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TerrainColumn;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Immutable layered support directory. A regional token hashes once and compares exact contents. */
public final class RegionalSurfaceMap extends AbstractMap<TerrainColumn, List<SurfaceAnchor>> {
    private static final RegionalSurfaceMap EMPTY = new RegionalSurfaceMap(Map.of());
    private static final class Columns {
        final Map<TerrainColumn, List<SurfaceAnchor>> values;
        final int hash;
        Columns(Map<TerrainColumn, List<SurfaceAnchor>> values) {
            var copy = new HashMap<TerrainColumn, List<SurfaceAnchor>>();
            values.forEach((column, levels) -> {
                if (levels.isEmpty() || levels.stream().anyMatch(surface -> surface.x() != column.x() || surface.z() != column.z()))
                    throw new IllegalArgumentException("support contribution differs from its column");
                copy.put(column, List.copyOf(levels));
            });
            this.values = Collections.unmodifiableMap(copy); hash = copy.hashCode();
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Columns columns && hash == columns.hash && values.equals(columns.values);
        }
    }
    private static final Columns EMPTY_COLUMNS = new Columns(Map.of());
    private final Map<Region, Columns> regions;
    private final int size, hash;
    private RegionalSurfaceMap(Map<Region, Columns> regions) {
        this.regions = Collections.unmodifiableMap(new HashMap<>(regions));
        size = regions.values().stream().mapToInt(columns -> columns.values.size()).sum();
        hash = regions.values().stream().mapToInt(Columns::hashCode).sum();
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
        return column instanceof TerrainColumn key ? in(region(key)).get(key) : null;
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
        if (this == previous) return Set.of();
        var changed = new HashSet<>(regions.keySet()); changed.addAll(previous.regions.keySet());
        changed.removeIf(region -> token(region).equals(previous.token(region)));
        return Set.copyOf(changed);
    }
    public RegionalSurfaceMap replace(Map<Region, Map<TerrainColumn, List<SurfaceAnchor>>> updates) {
        Map<Region, Columns> next = null;
        for (var entry : updates.entrySet()) {
            if (in(entry.getKey()).equals(entry.getValue())) continue;
            if (entry.getValue().keySet().stream().anyMatch(column -> !region(column).equals(entry.getKey())))
                throw new IllegalArgumentException("support contribution crosses its region");
            if (next == null) next = new HashMap<>(regions);
            if (entry.getValue().isEmpty()) next.remove(entry.getKey()); else next.put(entry.getKey(), new Columns(entry.getValue()));
        }
        return next == null ? this : new RegionalSurfaceMap(next);
    }
}
