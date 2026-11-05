package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** One optional initial-terrain provider. No task-specific geometry or route advice. */
final class GrayboxTerrainKnownGeometry implements KnownSpatialGeometry.Provider {
    private static final ImmutableResultView<String, KnownSpatialGeometry.Snapshot> SNAPSHOTS = new ImmutableResultView<>(8);
    @Override public KnownSpatialGeometry.Key key() { return KnownSpatialGeometry.Key.GRAYBOX_TERRAIN; }
    @Override public Set<NavigationBlockState> palette(FrontierWorldState state) { return plan(state).palette(); }
    private static GrayboxTerrainPlan plan(FrontierWorldState state) {
        return GrayboxTerrainPlan.forLayout(state.bootstrap().ruleset().worldLayout(), state.bootstrap().terrain().baselineSupportY());
    }
    @Override public KnownSpatialGeometry.Snapshot snapshot(FrontierWorldState state) { var plan = plan(state); return SNAPSHOTS.get(List.of(plan), "terrain", () -> new Snapshot(plan)); }
    private record Snapshot(GrayboxTerrainPlan plan) implements KnownSpatialGeometry.Snapshot {
        @Override public BoundedPedestrianApproach.SurveyedSurface overlay(BoundedPedestrianApproach.SurveyedSurface base) {
            if (plan.empty()) return base;
            return KnownBlockSurfaceOverlay.apply(base, position -> plan.collisionAt(position)
                    .map(cell -> new KnownBlockSurfaceOverlay.Cell(cell, KnownBlockSurfaceOverlay.Precedence.OBSERVATION_FIRST)),
                    column -> plan.column(column.x(), column.z()), area -> io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationRegionCoverage.intersects(plan.regions(), area));
        }
        @Override public Set<Region> regions() { return Set.of(); }
        @Override public Optional<Object> token(Region region) { return Optional.empty(); }
        @Override public Set<Region> changesFrom(KnownSpatialGeometry.Snapshot previous) {
            if (!(previous instanceof Snapshot before)) throw new IllegalArgumentException("foreign initial terrain snapshot");
            if (plan == before.plan) return Set.of();
            var changed = new HashSet<>(plan.regions()); changed.addAll(before.plan.regions()); return Set.copyOf(changed);
        }
    }
}
