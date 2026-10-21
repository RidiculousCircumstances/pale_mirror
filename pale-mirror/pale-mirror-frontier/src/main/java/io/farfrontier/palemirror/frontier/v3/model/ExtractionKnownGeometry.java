package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;

/** Extraction supplies block facts; shared regional projection owns navigation interpretation. */
final class ExtractionKnownGeometry implements KnownSiteGeometry.Provider {
    private record DepositView(ExtractionGeometryFacts facts, RegionalNavigationProjection projection) {
        DepositView() { this(new ExtractionGeometryFacts(), new RegionalNavigationProjection()); }
    }
    private final ImmutableInputView<KnownSiteGeometry.View> cache = new ImmutableInputView<>();
    private Object projectedBootstrap;
    private final Map<SubjectId, DepositView> deposits = new HashMap<>();
    private RegionalSurfaceContributions<SubjectId> surfaceParts = new RegionalSurfaceContributions<>();
    private RegionalBlockContributions<SubjectId> obstacleParts = new RegionalBlockContributions<>();
    private RegionalBlockContributions<SubjectId> withdrawnParts = new RegionalBlockContributions<>();
    private RegionalBlockContributions<SubjectId> unknownParts = new RegionalBlockContributions<>();
    private RegionalBlockMapContributions<SubjectId, NavigationBlockState> blockParts = new RegionalBlockMapContributions<>();
    private KnownSiteGeometry.View view;
    @Override public KnownSiteGeometry.Owner owner() { return KnownSiteGeometry.Owner.EXTRACTION; }
    @Override public Object version(FrontierWorldState state) { return project(state); }
    @Override public KnownSiteGeometry.View project(FrontierWorldState state) {
        return cache.get(List.of(state.bootstrap(), state.extractionSites().deposits(), state.extractionSites().geologicalExclusions(),
                state.extractionSites().geology(), state.extractionSites().knowledge(), state.navigationGeometry().definitions()), () -> refresh(state));
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
