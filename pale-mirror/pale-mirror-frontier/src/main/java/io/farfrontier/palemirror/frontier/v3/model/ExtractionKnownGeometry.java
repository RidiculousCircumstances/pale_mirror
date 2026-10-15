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
    private RegionalBlockContributions<SubjectId> unknownParts = new RegionalBlockContributions<>();
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
            unknownParts = new RegionalBlockContributions<>();
            projectedBootstrap = state.bootstrap();
        }
        var current = state.extractionSites().deposits();
        for (var removed : Set.copyOf(deposits.keySet())) if (!current.containsKey(removed)) {
            deposits.remove(removed); surfaceParts.replace(removed, RegionalSurfaceMap.copyOf(Map.of()));
            obstacleParts.replace(removed, RegionalBlockSet.copyOf(Set.of())); withdrawnParts.replace(removed, RegionalBlockSet.copyOf(Set.of()));
            unknownParts.replace(removed, RegionalBlockSet.copyOf(Set.of()));
        }
        for (var entry : current.entrySet()) {
            var deposit = entry.getValue();
            var view = deposits.computeIfAbsent(entry.getKey(), ignored -> new ImmutableInputView<>())
                    .get(List.of(deposit.site().layout(), deposit.cells(), deposit.geometry(), relevantExclusions),
                            () -> projectDeposit(state, deposit));
            surfaceParts.replace(entry.getKey(), RegionalSurfaceMap.copyOf(view.supports()));
            obstacleParts.replace(entry.getKey(), RegionalBlockSet.copyOf(view.obstacles()));
            withdrawnParts.replace(entry.getKey(), RegionalBlockSet.copyOf(view.withdrawnSupports()));
            unknownParts.replace(entry.getKey(), RegionalBlockSet.copyOf(view.unknown()));
        }
        return new KnownSiteGeometry.View(surfaceParts.snapshot(), obstacleParts.snapshot(), withdrawnParts.snapshot(), unknownParts.snapshot());
    }
    private KnownSiteGeometry.View projectDeposit(FrontierWorldState state, ExtractionDeposit deposit) {
        var working = new ExtractionWorkingSurfaces(state, deposit);
        var projection = KnownBlockNavigationProjection.project(footprint(deposit),
                position -> working.at(position).map(block -> KnownBlockClearance.navigationCell(block.kind())));
        return new KnownSiteGeometry.View(projection.supports(), projection.volumes(), projection.absentSupports(), projection.unknown());
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
