package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianSharedEstimateTest {
    @Test void selectedLegsAreRequestedTogetherAndNoPartialBatchBecomesAJourney() throws Exception {
        var revision = PedestrianGeometryRevision.initial(); var geometry = geometry(revision);
        var start = SurfaceAnchor.at(1, 63, 1); var end = SurfaceAnchor.at(40, 63, 1);
        var legs = List.of(new PedestrianRouteLeg(start, end), new PedestrianRouteLeg(end, start));
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner)) {
            var prepared = PedestrianRoutePlanning.prepare(planner.readView(), () -> {
                try { KnownPedestrianNavigation.estimateBatch(legs, origin -> geometry); return "READY"; }
                catch (KnownPedestrianNavigation.RouteUnavailable waiting) { return waiting.status().name(); }
            });
            assertEquals("PLANNING", prepared.observation().result());
            assertEquals(2, prepared.observation().requests().size(), "one selected batch declares both directed legs");
            prepared.acquire();
            for (int turn = 0; turn < 100 && planner.pendingCount() != 0; turn++) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            var ready = PedestrianRoutePlanning.prepare(planner.readView(), () -> KnownPedestrianNavigation.estimateBatch(legs, origin -> geometry));
            assertEquals(2, ready.observation().result().size());
            assertEquals(start, ready.observation().result().getFirst().origin());
            assertEquals(end, ready.observation().result().getLast().origin());
        }
    }
    @Test void oneImpossibleLegDoesNotRetainAnotherLegAsAnExecutionDependency() {
        var geometry = geometry(PedestrianGeometryRevision.initial());
        var from = SurfaceAnchor.at(1, 63, 1); var to = SurfaceAnchor.at(40, 63, 1);
        PedestrianRoutePlanner view = new PedestrianRoutePlanner() {
            public PedestrianRouteResult query(PedestrianRouteGeometry ignored, SurfaceAnchor start, SurfaceAnchor target) {
                return new PedestrianRouteResult(start.equals(from) ? PedestrianRouteResult.Status.PLANNING : PedestrianRouteResult.Status.NO_PATH,
                        List.of(), 0, 0, "fixture directed result");
            }
        };
        var prepared = PedestrianRoutePlanning.prepare(view, () -> {
            try { KnownPedestrianNavigation.estimateBatch(List.of(new PedestrianRouteLeg(from, to), new PedestrianRouteLeg(to, from)), origin -> geometry); return "READY"; }
            catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return unavailable.status().name(); }
        });
        assertEquals("NO_PATH", prepared.observation().result());
        assertTrue(prepared.observation().requests().isEmpty(), "terminal batch refusal cannot create a phantom wait");
    }
    @Test void positiveEstimateOutlivesDeliveryEvictionButNotChangedGeometry() {
        var revision = PedestrianGeometryRevision.initial(); var geometry = geometry(revision);
        var start = SurfaceAnchor.at(1, 63, 1); var target = SurfaceAnchor.at(40, 63, 1);
        try (var planner = new CooperativePedestrianPlanner()) {
            estimate(planner, geometry, start, target);
            var captured = planner.readView();
            for (int i = 0; i < 140; i++) estimate(planner, geometry, start, SurfaceAnchor.at(60 + i, 63, 2));
            long completed = planner.completedCount();
            assertEquals(PedestrianRouteResult.Status.ESTIMATED, planner.query(geometry, start, target, PedestrianRouteExtent.ESTIMATE).status());
            assertEquals(completed, planner.completedCount(), "consumed scalar facts need no second search");
            assertTrue(planner.readFact(PedestrianRouteRequest.of(geometry, start, target, PedestrianRouteExtent.ESTIMATE)).isPresent());
            assertEquals(PedestrianRouteResult.Status.ESTIMATED, captured.query(geometry, start, target, PedestrianRouteExtent.ESTIMATE).status());
            var changed = geometry(revision.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0))));
            assertTrue(planner.peek(changed, start, target, PedestrianRouteExtent.ESTIMATE).isEmpty());
            assertEquals(PedestrianRouteResult.Status.PLANNING, captured.query(changed, start, target, PedestrianRouteExtent.ESTIMATE).status());
            assertTrue(planner.estimateAtlasDiagnostic().get("units") <= 131_072);
        }
    }
    private static void estimate(CooperativePedestrianPlanner planner, PedestrianRouteGeometry geometry, SurfaceAnchor from, SurfaceAnchor to) {
        var result = planner.query(geometry, from, to, PedestrianRouteExtent.ESTIMATE);
        for (int turn = 0; turn < 1000 && result.status() == PedestrianRouteResult.Status.PLANNING; turn++) {
            planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            result = planner.query(geometry, from, to, PedestrianRouteExtent.ESTIMATE);
        }
        assertEquals(PedestrianRouteResult.Status.ESTIMATED, result.status());
    }
    private static PedestrianRouteGeometry geometry(PedestrianGeometryRevision revision) {
        return new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 256, 32); }
            public OptionalInt uniformSupport(WorldBounds area) { return OptionalInt.of(63); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
        };
    }
}
