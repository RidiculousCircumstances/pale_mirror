package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** The extraction owner contributes only retained geometry/depletion, not worker-specific obstacle policy. */
final class ExtractionKnownGeometry implements KnownSiteGeometry.Provider {
    private final ImmutableInputView<Object> version = new ImmutableInputView<>();
    private final ImmutableInputView<Set<BlockPosition>> queryFootprint = new ImmutableInputView<>();
    private Set<BlockPosition> priorQueries;
    private io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockExclusions priorExclusions;
    private Set<BlockPosition> relevantExclusions = Set.of();
    @Override public KnownSiteGeometry.Owner owner() { return KnownSiteGeometry.Owner.EXTRACTION; }
    @Override public Object version(FrontierWorldState state) {
        var declarations = new ArrayList<Object>();
        declarations.add(state.bootstrap());
        state.extractionSites().deposits().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> declarations.add(entry.getValue().site().layout()));
        var queries = queryFootprint.get(declarations, () -> {
            var positions = new HashSet<BlockPosition>();
            for (var deposit : state.extractionSites().deposits().values()) {
                var layout = deposit.site().layout();
                // Working contact probes support and both body cells; projection also
                // reads retained natural blocks. No other baseline voxel is queried.
                for (var surface : layout.accessSurfaces()) {
                    positions.add(surface.support());
                    positions.add(surface.support().offset(0, 1, 0));
                    positions.add(surface.support().offset(0, 2, 0));
                }
                positions.addAll(layout.terrainSupports().keySet());
            }
            return Set.copyOf(positions);
        });
        var exclusions = state.extractionSites().geologicalExclusions();
        if (priorQueries != queries || priorExclusions != exclusions) {
            boolean unchanged = priorQueries == queries && priorExclusions != null
                    && exclusions.additionsSince(priorExclusions)
                        .filter(added -> Collections.disjoint(added, queries)).isPresent();
            if (!unchanged) {
                var relevant = new HashSet<BlockPosition>();
                for (var position : queries) if (exclusions.positions().contains(position)) relevant.add(position);
                if (!relevant.equals(relevantExclusions)) relevantExclusions = Set.copyOf(relevant);
            }
            priorQueries = queries; priorExclusions = exclusions;
        }
        return version.get(List.of(state.bootstrap(), state.extractionSites().deposits(),
                relevantExclusions), Object::new);
    }
    @Override public KnownSiteGeometry.View project(FrontierWorldState state) {
        var supports = new LinkedHashMap<TerrainColumn, SurfaceAnchor>();
        var obstacles = new HashSet<BlockPosition>();
        for (var deposit : state.extractionSites().deposits().values()) {
            var layout = deposit.site().layout();
            var working = new ExtractionWorkingSurfaces(state, deposit);
            var active = working.current();
            var floorBlocks = new HashSet<BlockPosition>();
            var localColumns = new HashSet<TerrainColumn>();
            for (var surface : layout.accessSurfaces()) {
                var column = new TerrainColumn(surface.x(), surface.z());
                if (!localColumns.add(column)) continue;
                var current = active.get(column);
                if (current != null) floorBlocks.add(current.support());
                // Closed/unknown landings cannot fall back to the untouched bootstrap datum.
                if (supports.putIfAbsent(column, current == null ? surface : current) != null)
                    throw new IllegalArgumentException("extraction sites declare competing working columns");
                if (current == null) obstacles.add(supports.get(column).support().offset(0, 1, 0));
            }
            layout.fixedBlocks().forEach((position, block) -> {
                var observed = deposit.geometry().get(position);
                var actual = observed == null ? block : observed.block();
                if (!actual.kind().equals("minecraft:air") && !floorBlocks.contains(position)) obstacles.add(position);
            });
            layout.terrainSupports().keySet().forEach(position -> {
                var actual = working.at(position);
                if (!floorBlocks.contains(position) && (actual.isEmpty() || !actual.orElseThrow().kind().equals("minecraft:air")))
                    obstacles.add(position);
            });
            for (var cell : layout.cells())
                if (!deposit.cells().get(cell.id()).knownBlock().kind().equals("minecraft:air")
                        && !floorBlocks.contains(cell.source())) obstacles.add(cell.source());
        }
        return new KnownSiteGeometry.View(supports, obstacles);
    }
}
