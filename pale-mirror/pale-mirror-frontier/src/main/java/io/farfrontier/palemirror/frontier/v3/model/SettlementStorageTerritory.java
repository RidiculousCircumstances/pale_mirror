package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Admission-only default ownership of declared settlement/worksite land. Not behavior dispatch. */
public final class SettlementStorageTerritory {
    public record Area(Settlement settlement, int minX, int maxX, int minZ, int maxZ) {
        public boolean contains(BlockPosition position) {
            return position.x() >= minX && position.x() <= maxX && position.z() >= minZ && position.z() <= maxZ;
        }
    }
    private static FrontierBootstrap cached;
    private static List<Area> areas = List.of();
    private static final ImmutableInputView<List<Area>> WORK_AREAS = new ImmutableInputView<>();
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
    public static List<Area> areas(FrontierWorldState state) {
        var sites = state.extractionSites().deposits().values().stream().map(deposit -> deposit.site())
                .sorted(Comparator.comparing(site -> site.id())).toList();
        // Key the immutable declarations, not the newly allocated discovery list.
        // Work/custody changes retain site identity; expansion/replacement does not.
        var inputs = new ArrayList<Object>(sites.size() + 1);
        inputs.add(state.bootstrap()); inputs.addAll(sites);
        return WORK_AREAS.get(inputs, () -> {
        var result = new ArrayList<>(areas(state.bootstrap()));
        for (var site : sites) {
            var home = FrontierWorldStateSupport.settlement(state.bootstrap(), site.settlementId());
            var positions = new ArrayList<>(site.layout().fixedBlocks().keySet());
            site.layout().cells().forEach(cell -> positions.add(cell.source()));
            result.add(new Area(home, positions.stream().mapToInt(BlockPosition::x).min().orElseThrow(),
                    positions.stream().mapToInt(BlockPosition::x).max().orElseThrow(),
                    positions.stream().mapToInt(BlockPosition::z).min().orElseThrow(),
                    positions.stream().mapToInt(BlockPosition::z).max().orElseThrow()));
        }
        return List.copyOf(result);
        });
    }
    public static Optional<Settlement> owner(FrontierWorldState state, BlockPosition position) {
        if (!state.bootstrap().bounds().contains(position)) return Optional.empty();
        var homes = areas(state).stream().filter(area -> area.contains(position)).map(Area::settlement).distinct().toList();
        return homes.size() == 1 ? Optional.of(homes.getFirst()) : Optional.empty();
    }
}
