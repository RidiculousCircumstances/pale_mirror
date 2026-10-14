package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TerrainColumn;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Shared geometry composition, preserving every declared level in changed columns. */
public final class RegionalSurfaceContributions<K> {
    private final boolean disjointOwners;
    private final Map<K, RegionalSurfaceMap> sources = new HashMap<>();
    private RegionalSurfaceMap snapshot = RegionalSurfaceMap.copyOf(Map.of());
    public RegionalSurfaceContributions() { this(false); }
    public RegionalSurfaceContributions(boolean disjointOwners) { this.disjointOwners = disjointOwners; }
    public RegionalSurfaceMap replace(K owner, RegionalSurfaceMap contribution) {
        Objects.requireNonNull(owner); Objects.requireNonNull(contribution);
        var previous = sources.getOrDefault(owner, RegionalSurfaceMap.copyOf(Map.of()));
        var changed = contribution.changesFrom(previous);
        if (contribution.isEmpty()) sources.remove(owner); else sources.put(owner, contribution);
        var updates = new HashMap<Region, Map<TerrainColumn, List<SurfaceAnchor>>>();
        for (var region : changed) {
            var columns = new HashMap<TerrainColumn, List<SurfaceAnchor>>();
            for (var source : sources.values()) source.in(region).forEach((column, levels) -> columns.merge(column, levels,
                    (left, right) -> {
                        if (disjointOwners && !Collections.disjoint(left, right))
                            throw new IllegalArgumentException("two geometry owners declared one exact support");
                        return java.util.stream.Stream.concat(left.stream(), right.stream()).distinct()
                                .sorted(Comparator.comparingInt(SurfaceAnchor::y)).toList();
                    }));
            updates.put(region, columns);
        }
        snapshot = snapshot.replace(updates); return snapshot;
    }
    public RegionalSurfaceMap snapshot() { return snapshot; }
}
