package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import java.util.function.Predicate;

/** Bounded geometric selection only. No quarry, commodity, actor, world write or resource accounting. */
public final class AdjacentExcavationPlanner {
    public static final int MAX_STATIONS = 32_768;
    public static final int MAX_COLUMNS = 128;
    public static final int MAX_CLEARANCE_HEIGHT = 8;
    public record Column<B>(SurfaceAnchor station, SurfaceAnchor floor,
                            KnownBlockGeometry.Sample<B> support,
                            KnownBlockGeometry.Sample<B> upper,
                            KnownBlockGeometry.Sample<B> lower,
                            List<KnownBlockGeometry.Sample<B>> overburden) {
        public Column {
            overburden = List.copyOf(overburden);
            if (overburden.size() > MAX_CLEARANCE_HEIGHT - 2)
                throw new IllegalArgumentException("unbounded excavation overburden");
        }
        public Column(SurfaceAnchor station, SurfaceAnchor floor, KnownBlockGeometry.Sample<B> support,
                      KnownBlockGeometry.Sample<B> upper, KnownBlockGeometry.Sample<B> lower) {
            this(station, floor, support, upper, lower, List.of());
        }
        /** Bottom-up known samples; the execution owner declares its own top-down effects. */
        public List<KnownBlockGeometry.Sample<B>> clearance() {
            var result = new ArrayList<KnownBlockGeometry.Sample<B>>(List.of(lower, upper));
            result.addAll(overburden); return List.copyOf(result);
        }
    }
    /** Geometric strategy only. The caller declares ordering, not world edits or route authority. */
    public interface FrontSelection {
        List<SurfaceAnchor> order(Collection<SurfaceAnchor> stations);
        List<Integer> floorOffsets(SurfaceAnchor station, int dx, int dz);
        default int clearanceTopY(SurfaceAnchor station, SurfaceAnchor floor) { return floor.y() + 2; }
    }
    private static final FrontSelection ADJACENT = new FrontSelection() {
        @Override public List<SurfaceAnchor> order(Collection<SurfaceAnchor> stations) {
            return stations.stream().distinct().sorted(Comparator.comparingInt(SurfaceAnchor::x)
                    .thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList();
        }
        @Override public List<Integer> floorOffsets(SurfaceAnchor station, int dx, int dz) { return List.of(0, -1); }
    };
    private AdjacentExcavationPlanner() { }
    public static FrontSelection adjacentSelection() { return ADJACENT; }
    /** Validate a selected bounded front against current facts, not a second preferred proposal. */
    public static <B> void requireSelection(Collection<SurfaceAnchor> stations, List<Column<B>> columns,
            KnownBlockGeometry<B> geometry, Predicate<SurfaceAnchor> permittedFront,
            Predicate<B> supportsStanding, Predicate<B> mineable, int maxColumns, FrontSelection selection) {
        if (stations.isEmpty() || stations.size() > MAX_STATIONS || maxColumns < 1 || maxColumns > MAX_COLUMNS
                || columns.isEmpty() || columns.size() > maxColumns) throw new IllegalArgumentException("unbounded selected excavation front");
        var declared = Set.copyOf(stations);
        var seen = declared.stream().map(surface -> new TerrainColumn(surface.x(), surface.z()))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        for (var column : columns) {
            var station = column.station(); var floor = column.floor();
            int dx = floor.x() - station.x(), dz = floor.z() - station.z(), dy = floor.y() - station.y();
            if (!declared.contains(station) || Math.abs(dx) + Math.abs(dz) != 1 || dy != 0 && dy != -1
                    || !selection.floorOffsets(station, dx, dz).contains(dy)
                    || !seen.add(new TerrainColumn(floor.x(), floor.z())) || !permittedFront.test(floor)
                    || !geometry.at(floor.support()).equals(Optional.of(column.support()))
                    || column.support().permission() != KnownBlockGeometry.Sample.Permission.PUBLIC
                    || !supportsStanding.test(column.support().block()))
                throw new IllegalArgumentException("selected excavation violates current support, access or terrace limits");
            int height = selection.clearanceTopY(station, floor) - floor.y();
            if (height < 2 || height > MAX_CLEARANCE_HEIGHT || column.clearance().size() != height)
                throw new IllegalArgumentException("selected excavation has a changed cut height");
            for (int y = 1; y <= height; y++) {
                var sample = column.clearance().get(y - 1);
                if (!geometry.at(floor.support().offset(0, y, 0)).equals(Optional.of(sample))
                        || sample.permission() != KnownBlockGeometry.Sample.Permission.PUBLIC || !mineable.test(sample.block()))
                    throw new IllegalArgumentException("selected excavation has unknown, changed or protected material");
            }
        }
    }
    public static <B> List<Column<B>> propose(Collection<SurfaceAnchor> stations, KnownBlockGeometry<B> geometry,
            Predicate<SurfaceAnchor> reachable, Predicate<SurfaceAnchor> permittedFront,
            Predicate<B> supportsStanding, Predicate<B> mineable, int maxColumns) {
        return propose(stations, geometry, reachable, permittedFront, supportsStanding, mineable, maxColumns, ADJACENT);
    }
    public static <B> List<Column<B>> propose(Collection<SurfaceAnchor> stations, KnownBlockGeometry<B> geometry,
            Predicate<SurfaceAnchor> reachable, Predicate<SurfaceAnchor> permittedFront,
            Predicate<B> supportsStanding, Predicate<B> mineable, int maxColumns, FrontSelection selection) {
        if (stations.size() > MAX_STATIONS || maxColumns < 1 || maxColumns > MAX_COLUMNS)
            throw new IllegalArgumentException("unbounded adjacent excavation query");
        var ordered = selection.order(stations);
        if (ordered.size() != Set.copyOf(stations).size() || !Set.copyOf(ordered).equals(Set.copyOf(stations)))
            throw new IllegalArgumentException("excavation strategy changed its retained station set");
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
                for (int descent : selection.floorOffsets(station, direction[0], direction[1])) {
                if (descent != 0 && descent != -1) throw new IllegalArgumentException("excavation strategy requires an unsafe drop");
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
                int height = selection.clearanceTopY(station, new SurfaceAnchor(floor)) - floor.y();
                if (height < 2 || height > MAX_CLEARANCE_HEIGHT) continue;
                var overburden = new ArrayList<KnownBlockGeometry.Sample<B>>();
                boolean knownCut = true;
                for (int y = 3; y <= height; y++) {
                    var sample = geometry.at(floor.offset(0, y, 0));
                    if (sample.isEmpty() || sample.orElseThrow().permission() != KnownBlockGeometry.Sample.Permission.PUBLIC
                            || !mineable.test(sample.orElseThrow().block())) { knownCut = false; break; }
                    overburden.add(sample.orElseThrow());
                }
                if (!knownCut) continue;
                if (admitted == null) admitted = reachable.test(station);
                if (!admitted) continue;
                seen.add(column);
                result.add(new Column<>(station, new SurfaceAnchor(floor), support.get(), upper.get(), lower.get(), overburden));
                if (result.size() == maxColumns) return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }
}
