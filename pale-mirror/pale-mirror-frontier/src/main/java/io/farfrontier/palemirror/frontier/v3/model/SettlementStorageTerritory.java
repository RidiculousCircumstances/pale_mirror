package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Admission-only default ownership inside the existing residential perimeter. Not behavior dispatch. */
public final class SettlementStorageTerritory {
    public record Area(Settlement settlement, int minX, int maxX, int minZ, int maxZ) {
        public boolean contains(BlockPosition position) {
            return position.x() >= minX && position.x() <= maxX && position.z() >= minZ && position.z() <= maxZ;
        }
    }
    private static FrontierBootstrap cached;
    private static List<Area> areas = List.of();
    private SettlementStorageTerritory() { }
    public static synchronized List<Area> areas(FrontierBootstrap bootstrap) {
        if (cached != bootstrap) {
            areas = bootstrap.settlements().stream().map(settlement -> {
                var apron = SettlementResidentIngressPlan.compile(bootstrap.bounds(), bootstrap.terrain(), settlement,
                        bootstrap.ruleset().facilityCapacity().intactHousingBeds()).perimeterSurfaces();
                return new Area(settlement, apron.stream().mapToInt(SurfaceAnchor::x).min().orElseThrow(),
                        apron.stream().mapToInt(SurfaceAnchor::x).max().orElseThrow(),
                        apron.stream().mapToInt(SurfaceAnchor::z).min().orElseThrow(),
                        apron.stream().mapToInt(SurfaceAnchor::z).max().orElseThrow());
            }).toList();
            cached = bootstrap;
        }
        return areas;
    }
    public static Optional<Settlement> owner(FrontierBootstrap bootstrap, BlockPosition position) {
        if (!bootstrap.bounds().contains(position)) return Optional.empty();
        var candidates = areas(bootstrap).stream().filter(area -> area.contains(position)).toList();
        // An overlap is not permission to choose the nearest or first owner.
        return candidates.size() == 1 ? Optional.of(candidates.getFirst().settlement()) : Optional.empty();
    }
}
