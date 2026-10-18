package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit;

/** The extraction owner contributes only retained geometry/depletion, not worker-specific obstacle policy. */
final class ExtractionKnownGeometry implements KnownSiteGeometry.Provider {
    private final ImmutableInputView<Object> version = new ImmutableInputView<>();
    private final ImmutableInputView<Set<BlockPosition>> queryFootprint = new ImmutableInputView<>();
    private final LocalBlockExclusions exclusions = new LocalBlockExclusions();
    private Object projectedBootstrap;
    private final Map<SubjectId, ImmutableInputView<KnownSiteGeometry.View>> deposits = new HashMap<>();
    private final Map<SubjectId, ImmutableInputView<Set<BlockPosition>>> footprints = new HashMap<>();
    private final Map<SubjectId, LocalBlockExclusions> localExclusions = new HashMap<>();
    private RegionalSurfaceContributions<SubjectId> surfaceParts = new RegionalSurfaceContributions<>();
    private RegionalBlockContributions<SubjectId> obstacleParts = new RegionalBlockContributions<>();
    private RegionalBlockContributions<SubjectId> withdrawnParts = new RegionalBlockContributions<>();
    private RegionalBlockContributions<SubjectId> unknownParts = new RegionalBlockContributions<>();
    private RegionalBlockMapContributions<SubjectId, NavigationBlockState> blockParts = new RegionalBlockMapContributions<>();
    @Override public KnownSiteGeometry.Owner owner() { return KnownSiteGeometry.Owner.EXTRACTION; }
    @Override public Object version(FrontierWorldState state) {
        var declarations = new ArrayList<Object>();
        declarations.add(state.bootstrap());
        state.extractionSites().deposits().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> { declarations.add(entry.getValue().site().layout()); declarations.add(entry.getValue().geometry()); });
        var queries = queryFootprint.get(declarations, () -> {
            var positions = new HashSet<BlockPosition>();
            for (var deposit : state.extractionSites().deposits().values()) {
                positions.addAll(footprint(deposit));
            }
            return Set.copyOf(positions);
        });
        var relevantExclusions = exclusions.read(queries, state.extractionSites().geologicalExclusions());
        return version.get(List.of(state.bootstrap(), state.extractionSites().deposits(),
                relevantExclusions, state.extractionSites().geology(), state.extractionSites().knowledge(),
                state.navigationGeometry().definitions()), Object::new);
    }
    @Override public KnownSiteGeometry.View project(FrontierWorldState state) {
        if (projectedBootstrap != state.bootstrap()) {
            deposits.clear(); footprints.clear(); localExclusions.clear(); surfaceParts = new RegionalSurfaceContributions<>();
            obstacleParts = new RegionalBlockContributions<>(); withdrawnParts = new RegionalBlockContributions<>();
            unknownParts = new RegionalBlockContributions<>();
            blockParts = new RegionalBlockMapContributions<>();
            projectedBootstrap = state.bootstrap();
        }
        var current = state.extractionSites().deposits();
        for (var removed : Set.copyOf(deposits.keySet())) if (!current.containsKey(removed)) {
            footprints.remove(removed); localExclusions.remove(removed);
            deposits.remove(removed); surfaceParts.replace(removed, RegionalSurfaceMap.copyOf(Map.of()));
            obstacleParts.replace(removed, RegionalBlockSet.copyOf(Set.of())); withdrawnParts.replace(removed, RegionalBlockSet.copyOf(Set.of()));
            unknownParts.replace(removed, RegionalBlockSet.copyOf(Set.of()));
            blockParts.replace(removed, RegionalBlockMap.copyOf(Map.of()));
        }
        for (var entry : current.entrySet()) {
            var deposit = entry.getValue();
            var queries = footprints.computeIfAbsent(entry.getKey(), ignored -> new ImmutableInputView<>())
                    .get(List.of(deposit.site().layout(), deposit.geometry()), () -> footprint(deposit));
            var relevant = localExclusions.computeIfAbsent(entry.getKey(), ignored -> new LocalBlockExclusions())
                    .read(queries, state.extractionSites().geologicalExclusions());
            var view = deposits.computeIfAbsent(entry.getKey(), ignored -> new ImmutableInputView<>())
                    .get(List.of(deposit.site(), deposit.cells(), deposit.geometry(), relevant,
                            state.extractionSites().geology(), state.extractionSites().knowledge(), state.navigationGeometry().definitions()),
                            () -> projectDeposit(state, deposit, queries));
            surfaceParts.replace(entry.getKey(), RegionalSurfaceMap.copyOf(view.supports()));
            obstacleParts.replace(entry.getKey(), RegionalBlockSet.copyOf(view.obstacles()));
            withdrawnParts.replace(entry.getKey(), RegionalBlockSet.copyOf(view.withdrawnSupports()));
            unknownParts.replace(entry.getKey(), RegionalBlockSet.copyOf(view.unknown()));
            blockParts.replace(entry.getKey(), RegionalBlockMap.copyOf(view.retainedBlocks()));
        }
        return new KnownSiteGeometry.View(surfaceParts.snapshot(), obstacleParts.snapshot(), withdrawnParts.snapshot(), unknownParts.snapshot(), blockParts.snapshot());
    }
    private KnownSiteGeometry.View projectDeposit(FrontierWorldState state, ExtractionDeposit deposit, Set<BlockPosition> queries) {
        var working = new ExtractionWorkingSurfaces(state, deposit);
        var blocks = new HashMap<BlockPosition, NavigationBlockState>();
        for (var position : queries) working.at(position).ifPresent(block ->
                blocks.put(position, new NavigationBlockState(block.kind(), block.properties())));
        var projection = KnownBlockNavigationProjection.project(queries, position -> {
            var block = blocks.get(position); if (block == null) return Optional.empty();
            return KnownBlockCollision.coarse(state.navigationGeometry(), block);
        });
        return new KnownSiteGeometry.View(projection.supports(), projection.volumes(), projection.absentSupports(), projection.unknown(), blocks);
    }
    private static Set<BlockPosition> footprint(ExtractionDeposit deposit) {
        var layout = deposit.site().layout();
        var footprint = new HashSet<BlockPosition>(layout.terrainSupports().keySet());
        footprint.addAll(layout.fixedBlocks().keySet()); footprint.addAll(deposit.geometry().keySet());
        layout.accessSurfaces().forEach(surface -> footprint.add(surface.support()));
        layout.cells().forEach(cell -> footprint.add(cell.source()));
        // A ceiling above one contact can be the floor of another. Publish both
        // from the same immutable facts, independently of mining target selection.
        for (var position : Set.copyOf(footprint)) for (int height = 1; height <= 3; height++)
            footprint.add(position.offset(0, height, 0));
        return Set.copyOf(footprint);
    }
}
