package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;

/** Extraction supplies block facts; shared regional projection owns navigation interpretation. */
final class ExtractionKnownGeometry implements KnownSiteGeometry.Provider {
    private record DepositView(ExtractionGeometryFacts facts, RegionalNavigationProjection projection) {
        DepositView() { this(new ExtractionGeometryFacts(), new RegionalNavigationProjection()); }
    }
    private final ImmutableResultView<String, KnownSiteGeometry.View> completed = new ImmutableResultView<>(4);
    private volatile Projection preparedSeed;
    private final ThreadLocal<Projection> working = ThreadLocal.withInitial(() -> {
        var seed = preparedSeed;
        return seed == null ? new Projection() : new Projection(seed);
    });
    private final ExtractionNavigationPalette palette = new ExtractionNavigationPalette();
    @Override public Set<NavigationBlockState> palette(FrontierWorldState state) { return palette.read(state); }
    @Override public KnownSiteGeometry.Owner owner() { return KnownSiteGeometry.Owner.EXTRACTION; }
    @Override public KnownSiteGeometry.View project(FrontierWorldState state) {
        return completed.get(List.of(state.bootstrap(), state.extractionSites().deposits(), state.extractionSites().geologicalExclusions(),
                state.extractionSites().geology(), state.extractionSites().knowledge(), state.navigationGeometry().definitions()),
                "geometry", () -> {
                    var builder = working.get();
                    var result = builder.refresh(state);
                    var seed = preparedSeed;
                    if (seed == null || seed.view != result) preparedSeed = new Projection(builder);
                    return result;
                });
    }
    /** Mutable incremental builders belong to their computing thread. Only completed,
     * immutable views cross the preparation/owner boundary. No projection lock. */
    private static final class Projection {
        private Object projectedBootstrap;
        private final Map<SubjectId, DepositView> deposits = new HashMap<>();
        private RegionalSurfaceContributions<SubjectId> surfaceParts = new RegionalSurfaceContributions<>();
        private RegionalBlockContributions<SubjectId> obstacleParts = new RegionalBlockContributions<>();
        private RegionalBlockContributions<SubjectId> withdrawnParts = new RegionalBlockContributions<>();
        private RegionalBlockContributions<SubjectId> unknownParts = new RegionalBlockContributions<>();
        private RegionalBlockMapContributions<SubjectId, NavigationBlockState> blockParts = new RegionalBlockMapContributions<>();
        private KnownSiteGeometry.View view;
        Projection() { }
        /** Private frozen seeds are never refreshed; fork before any mutation. */
        Projection(Projection source) {
            projectedBootstrap = source.projectedBootstrap; view = source.view;
            source.deposits.forEach((id, part) -> deposits.put(id,
                    new DepositView(new ExtractionGeometryFacts(part.facts()), part.projection().fork())));
            surfaceParts = source.surfaceParts.fork(); obstacleParts = source.obstacleParts.fork();
            withdrawnParts = source.withdrawnParts.fork(); unknownParts = source.unknownParts.fork();
            blockParts = source.blockParts.fork();
        }
        private KnownSiteGeometry.View refresh(FrontierWorldState state) {
            if (projectedBootstrap != state.bootstrap()) {
                deposits.clear(); surfaceParts = new RegionalSurfaceContributions<>();
                obstacleParts = new RegionalBlockContributions<>(); withdrawnParts = new RegionalBlockContributions<>();
                unknownParts = new RegionalBlockContributions<>(); blockParts = new RegionalBlockMapContributions<>();
                view = null; projectedBootstrap = state.bootstrap();
            }
            var current = state.extractionSites().deposits();
            for (var removed : new ArrayList<>(deposits.keySet())) if (!current.containsKey(removed)) {
                deposits.remove(removed); surfaceParts.replace(removed, RegionalSurfaceMap.copyOf(Map.of()));
                obstacleParts.replace(removed, RegionalBlockSet.copyOf(Set.of())); withdrawnParts.replace(removed, RegionalBlockSet.copyOf(Set.of()));
                unknownParts.replace(removed, RegionalBlockSet.copyOf(Set.of())); blockParts.replace(removed, RegionalBlockMap.copyOf(Map.of()));
            }
            for (var entry : current.entrySet()) {
                var local = deposits.computeIfAbsent(entry.getKey(), ignored -> new DepositView());
                var result = local.projection().read(local.facts().read(state, entry.getValue()), state.navigationGeometry());
                var geometry = result.geometry();
                surfaceParts.replace(entry.getKey(), RegionalSurfaceMap.copyOf(geometry.supports()));
                obstacleParts.replace(entry.getKey(), RegionalBlockSet.copyOf(geometry.volumes()));
                withdrawnParts.replace(entry.getKey(), RegionalBlockSet.copyOf(geometry.absentSupports()));
                unknownParts.replace(entry.getKey(), RegionalBlockSet.copyOf(geometry.unknown()));
                blockParts.replace(entry.getKey(), result.blocks());
            }
            if (view == null || view.supports() != surfaceParts.snapshot() || view.obstacles() != obstacleParts.snapshot()
                    || view.withdrawnSupports() != withdrawnParts.snapshot() || view.unknown() != unknownParts.snapshot()
                    || view.retainedBlocks() != blockParts.snapshot())
                view = new KnownSiteGeometry.View(surfaceParts.snapshot(), obstacleParts.snapshot(), withdrawnParts.snapshot(), unknownParts.snapshot(), blockParts.snapshot());
            return view;
        }
    }
}
