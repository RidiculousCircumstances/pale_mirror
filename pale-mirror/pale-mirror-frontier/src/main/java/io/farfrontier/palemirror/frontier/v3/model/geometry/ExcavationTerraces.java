package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TerrainColumn;
import java.util.*;
import java.util.function.Predicate;

/** Reusable geometric policy: lower adjacent benches, with a real open run between drops. */
public record ExcavationTerraces(int maximumDepth, int minimumBenchRun) {
    public ExcavationTerraces {
        if (maximumDepth < 1 || maximumDepth > 32 || minimumBenchRun < 2 || minimumBenchRun > 32)
            throw new IllegalArgumentException("invalid bounded excavation terrace policy");
    }
    public String canonicalText() { return maximumDepth + ":" + minimumBenchRun; }
    public AdjacentExcavationPlanner.FrontSelection selection(int rootY, Collection<SurfaceAnchor> surfaces,
                                                              Predicate<SurfaceAnchor> open) {
        return selection(rootY, java.util.OptionalInt.empty(), surfaces, open);
    }
    public AdjacentExcavationPlanner.FrontSelection selection(int rootY, java.util.OptionalInt openingTopY,
            Collection<SurfaceAnchor> surfaces, Predicate<SurfaceAnchor> open) {
        if (surfaces.size() > AdjacentExcavationPlanner.MAX_STATIONS)
            throw new IllegalArgumentException("unbounded terrace surface query");
        var columns = new HashMap<TerrainColumn, SurfaceAnchor>();
        for (var surface : surfaces) if (columns.put(new TerrainColumn(surface.x(), surface.z()), surface) != null)
            throw new IllegalArgumentException("terrace plan has competing column heights");
        return new Selection(rootY, openingTopY, Map.copyOf(columns), open);
    }
    private final class Selection implements AdjacentExcavationPlanner.FrontSelection {
        private final int rootY;
        private final java.util.OptionalInt openingTopY;
        private final Map<TerrainColumn, SurfaceAnchor> columns;
        private final Predicate<SurfaceAnchor> open;
        private Selection(int rootY, java.util.OptionalInt openingTopY, Map<TerrainColumn, SurfaceAnchor> columns, Predicate<SurfaceAnchor> open) {
            this.rootY = rootY; this.openingTopY = Objects.requireNonNull(openingTopY);
            this.columns = columns; this.open = Objects.requireNonNull(open);
        }
        @Override public int clearanceTopY(SurfaceAnchor station, SurfaceAnchor floor) {
            return openingTopY.orElse(floor.y() + 2);
        }
        @Override public List<SurfaceAnchor> order(Collection<SurfaceAnchor> stations) {
            return stations.stream().distinct().sorted(Comparator.comparingInt(SurfaceAnchor::y)
                    .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z)).toList();
        }
        @Override public List<Integer> floorOffsets(SurfaceAnchor station, int dx, int dz) {
            // An existing higher entrance is protected access, not another excavation datum.
            if (station.y() > rootY || station.y() < rootY - maximumDepth) return List.of();
            if (station.y() == rootY - maximumDepth) return List.of(0);
            for (int step = 0; step < minimumBenchRun; step++) {
                var previous = columns.get(new TerrainColumn(station.x() - dx * step, station.z() - dz * step));
                if (previous == null || previous.y() != station.y() || !open.test(previous)) return List.of(0);
            }
            return List.of(-1, 0);
        }
    }
}
