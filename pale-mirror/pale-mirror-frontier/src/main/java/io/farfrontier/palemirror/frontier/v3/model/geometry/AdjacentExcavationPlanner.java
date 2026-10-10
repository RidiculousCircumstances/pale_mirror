package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import java.util.function.Predicate;

/** Bounded geometric selection only. No quarry, commodity, actor, world write or resource accounting. */
public final class AdjacentExcavationPlanner {
    public static final int MAX_STATIONS = 32_768;
    public static final int MAX_COLUMNS = 128;
    public record Column<B>(SurfaceAnchor station, SurfaceAnchor floor,
                            KnownBlockGeometry.Sample<B> support,
                            KnownBlockGeometry.Sample<B> upper,
                            KnownBlockGeometry.Sample<B> lower) { }
    private AdjacentExcavationPlanner() { }
    public static <B> List<Column<B>> propose(Collection<SurfaceAnchor> stations, KnownBlockGeometry<B> geometry,
            Predicate<SurfaceAnchor> reachable, Predicate<SurfaceAnchor> permittedFront,
            Predicate<B> supportsStanding, Predicate<B> mineable, int maxColumns) {
        if (stations.size() > MAX_STATIONS || maxColumns < 1 || maxColumns > MAX_COLUMNS)
            throw new IllegalArgumentException("unbounded adjacent excavation query");
        var ordered = stations.stream().distinct().sorted(Comparator.comparingInt(SurfaceAnchor::x)
                .thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList();
        var result = new ArrayList<Column<B>>();
        // This plan extends a single-surface column graph. Digging below an existing
        // access column would create competing heights and undermine its retained access.
        var seen = ordered.stream().map(surface -> new TerrainColumn(surface.x(), surface.z()))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        for (var station : ordered) {
            Boolean admitted = null;
            for (int[] direction : List.of(new int[]{0, -1}, new int[]{-1, 0}, new int[]{1, 0}, new int[]{0, 1})) {
                // A one-block descent is carved from known rock, never constructed or teleported.
                // Upward excavation requires a higher work stance and is not implied by this reach.
                for (int descent : List.of(0, -1)) {
                var floor = station.support().offset(direction[0], descent, direction[1]);
                var column = new TerrainColumn(floor.x(), floor.z());
                if (seen.contains(column) || !permittedFront.test(new SurfaceAnchor(floor))) continue;
                var support = geometry.at(floor); var lower = geometry.at(floor.offset(0, 1, 0));
                var upper = geometry.at(floor.offset(0, 2, 0));
                if (support.isEmpty() || lower.isEmpty() || upper.isEmpty()
                        || support.get().permission() != KnownBlockGeometry.Sample.Permission.PUBLIC
                        || lower.get().permission() != KnownBlockGeometry.Sample.Permission.PUBLIC
                        || upper.get().permission() != KnownBlockGeometry.Sample.Permission.PUBLIC
                        || !supportsStanding.test(support.get().block()) || !mineable.test(lower.get().block()) || !mineable.test(upper.get().block())) continue;
                if (admitted == null) admitted = reachable.test(station);
                if (!admitted) continue;
                seen.add(column);
                result.add(new Column<>(station, new SurfaceAnchor(floor), support.get(), upper.get(), lower.get()));
                if (result.size() == maxColumns) return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }
}
