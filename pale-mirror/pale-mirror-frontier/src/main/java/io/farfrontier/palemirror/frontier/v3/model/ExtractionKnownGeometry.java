package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit;

/** The extraction owner contributes only retained geometry/depletion, not worker-specific obstacle policy. */
final class ExtractionKnownGeometry implements KnownSiteGeometry.Provider {
    private final ImmutableInputView<Object> version = new ImmutableInputView<>();
    private final ImmutableInputView<Set<BlockPosition>> queryFootprint = new ImmutableInputView<>();
    private Set<BlockPosition> priorQueries;
    private io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockExclusions priorExclusions;
    private Set<BlockPosition> relevantExclusions = Set.of();
    private Object projectedBootstrap;
    private final Map<SubjectId, ImmutableInputView<KnownSiteGeometry.View>> deposits = new HashMap<>();
    private RegionalSurfaceContributions<SubjectId> surfaceParts = new RegionalSurfaceContributions<>();
    private RegionalBlockContributions<SubjectId> obstacleParts = new RegionalBlockContributions<>();
    private RegionalBlockContributions<SubjectId> withdrawnParts = new RegionalBlockContributions<>();
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
                // Working contact probes support, body and step headroom; projection also
                // reads retained natural blocks. No other baseline voxel is queried.
                var contacts = java.util.stream.Stream.concat(layout.accessSurfaces().stream(),
                        layout.cells().stream().map(cell -> new SurfaceAnchor(cell.source()))).toList();
                for (var surface : contacts) {
                    positions.add(surface.support());
                    positions.add(surface.support().offset(0, 1, 0));
                    positions.add(surface.support().offset(0, 2, 0));
                    positions.add(surface.support().offset(0, 3, 0)); // Swept headroom on a one-block step.
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
        if (projectedBootstrap != state.bootstrap()) {
            deposits.clear(); surfaceParts = new RegionalSurfaceContributions<>();
            obstacleParts = new RegionalBlockContributions<>(); withdrawnParts = new RegionalBlockContributions<>();
            projectedBootstrap = state.bootstrap();
        }
        var current = state.extractionSites().deposits();
        for (var removed : Set.copyOf(deposits.keySet())) if (!current.containsKey(removed)) {
            deposits.remove(removed); surfaceParts.replace(removed, RegionalSurfaceMap.copyOf(Map.of()));
            obstacleParts.replace(removed, RegionalBlockSet.copyOf(Set.of())); withdrawnParts.replace(removed, RegionalBlockSet.copyOf(Set.of()));
        }
        for (var entry : current.entrySet()) {
            var deposit = entry.getValue();
            var view = deposits.computeIfAbsent(entry.getKey(), ignored -> new ImmutableInputView<>())
                    .get(List.of(deposit.site().layout(), deposit.cells(), deposit.geometry(), relevantExclusions),
                            () -> projectDeposit(state, deposit));
            surfaceParts.replace(entry.getKey(), RegionalSurfaceMap.copyOf(view.supports()));
            obstacleParts.replace(entry.getKey(), RegionalBlockSet.copyOf(view.obstacles()));
            withdrawnParts.replace(entry.getKey(), RegionalBlockSet.copyOf(view.withdrawnSupports()));
        }
        return new KnownSiteGeometry.View(surfaceParts.snapshot(), obstacleParts.snapshot(), withdrawnParts.snapshot());
    }
    private KnownSiteGeometry.View projectDeposit(FrontierWorldState state, ExtractionDeposit deposit) {
        var supports = new LinkedHashMap<TerrainColumn, List<SurfaceAnchor>>();
        var obstacles = new HashSet<BlockPosition>();
        var withdrawn = new HashSet<BlockPosition>();
        {
            var layout = deposit.site().layout();
            var working = new ExtractionWorkingSurfaces(state, deposit);
            var active = working.allCurrent();
            var floorBlocks = new HashSet<BlockPosition>();
            for (var surface : active) {
                var column = new TerrainColumn(surface.x(), surface.z());
                floorBlocks.add(surface.support());
                var levels = new ArrayList<>(supports.getOrDefault(column, List.of()));
                if (!levels.contains(surface)) levels.add(surface);
                levels.sort(Comparator.comparingInt(SurfaceAnchor::y)); supports.put(column, List.copyOf(levels));
            }
            // Exact unavailable supports cannot be resurrected by bootstrap terrain.
            for (var surface : layout.accessSurfaces()) if (!active.contains(surface)) withdrawn.add(surface.support());
            layout.cells().forEach(cell -> {
                if (KnownBlockClearance.emptyCollision(deposit.cells().get(cell.id()).knownBlock().kind())) withdrawn.add(cell.source());
            });
            deposit.geometry().forEach((position, observation) -> {
                if (KnownBlockClearance.emptyCollision(observation.block().kind())) withdrawn.add(position);
            });
            layout.fixedBlocks().forEach((position, block) -> {
                var observed = deposit.geometry().get(position);
                var actual = observed == null ? block : observed.block();
                if (KnownBlockClearance.emptyCollision(actual.kind())) withdrawn.add(position);
                if (!KnownBlockClearance.emptyCollision(actual.kind()) && !floorBlocks.contains(position)) obstacles.add(position);
            });
            layout.terrainSupports().keySet().forEach(position -> {
                var actual = working.at(position);
                if (!floorBlocks.contains(position) && (actual.isEmpty() || !KnownBlockClearance.emptyCollision(actual.orElseThrow().kind())))
                    obstacles.add(position);
            });
            for (var cell : layout.cells())
                if (!KnownBlockClearance.emptyCollision(deposit.cells().get(cell.id()).knownBlock().kind())
                        && !floorBlocks.contains(cell.source())) obstacles.add(cell.source());
            // A clear standing pocket does not prove its incoming edge is clear.
            // Retain the known ceiling used by the common canTraverse query, even
            // when that block was never admitted as a mining resource cell.
            for (var surface : active) for (int height = 1; height <= 3; height++) {
                var bodyBlock = surface.support().offset(0, height, 0);
                if (working.at(bodyBlock).filter(block -> !KnownBlockClearance.emptyCollision(block.kind())).isPresent()
                        && !floorBlocks.contains(bodyBlock)) obstacles.add(bodyBlock);
            }
        }
        return new KnownSiteGeometry.View(supports, obstacles, withdrawn);
    }
}
