package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import java.util.function.Function;

/** Derives independent contacts and collision volumes from bounded immutable block facts.
 * A ceiling can also support an upper floor. No job, resource definition or actor owns that rule. */
public final class KnownBlockNavigationProjection {
    public enum Cell { EMPTY, FULL_SUPPORT, OBSTACLE }
    public record View(Map<TerrainColumn, List<SurfaceAnchor>> supports, Set<BlockPosition> volumes,
                       Set<BlockPosition> absentSupports, Set<BlockPosition> unknown) {
        public View {
            supports = RegionalSurfaceMap.copyOf(supports); volumes = RegionalBlockSet.copyOf(volumes);
            absentSupports = RegionalBlockSet.copyOf(absentSupports); unknown = RegionalBlockSet.copyOf(unknown);
        }
    }
    private KnownBlockNavigationProjection() { }

    public static View project(Set<BlockPosition> footprint, Function<BlockPosition, Optional<Cell>> facts) {
        var supports = new HashMap<TerrainColumn, List<SurfaceAnchor>>();
        var volumes = new HashSet<BlockPosition>(); var absent = new HashSet<BlockPosition>();
        var unknown = new HashSet<BlockPosition>();
        for (var position : footprint) {
            var cell = Objects.requireNonNull(facts.apply(position));
            if (cell.isEmpty()) { unknown.add(position); continue; }
            switch (cell.orElseThrow()) {
                case EMPTY -> absent.add(position);
                case OBSTACLE -> { volumes.add(position); absent.add(position); }
                case FULL_SUPPORT -> {
                    volumes.add(position);
                    supports.computeIfAbsent(new TerrainColumn(position.x(), position.z()), ignored -> new ArrayList<>())
                            .add(new SurfaceAnchor(position));
                }
            }
        }
        supports.replaceAll((column, levels) -> levels.stream().sorted(Comparator.comparingInt(SurfaceAnchor::y)).toList());
        return new View(RegionalSurfaceMap.copyOf(supports), RegionalBlockSet.copyOf(volumes),
                RegionalBlockSet.copyOf(absent), RegionalBlockSet.copyOf(unknown));
    }
}
