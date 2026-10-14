package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianIncrementalRoutingTest {
    @Test void upperGraphCombinesIntersectingTrunksForANewDestinationWithoutResurveyingTheWorld() {
        var revision = PedestrianGeometryRevision.initial(); var reads = new java.util.concurrent.atomic.AtomicInteger();
        var geometry = new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, -512, 1024, 1024); }
            public SurfaceAnchor supportAt(int x, int z) { reads.incrementAndGet(); return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
        };
        try (var planner = new CooperativePedestrianPlanner()) {
            var extent = PedestrianRouteExtent.COMPLETE;
            var east = finish(planner, geometry, SurfaceAnchor.at(1, 63, 1), SurfaceAnchor.at(960, 63, 1), extent);
            var south = finish(planner, geometry, SurfaceAnchor.at(480, 63, -480), SurfaceAnchor.at(480, 63, 480), extent);
            assertEquals(PedestrianRouteResult.Status.FOUND, east.status());
            assertEquals(PedestrianRouteResult.Status.FOUND, south.status());
            reads.set(0);
            var from = SurfaceAnchor.at(32, 63, 1); var to = SurfaceAnchor.at(480, 63, 400);
            var joined = finish(planner, geometry, from, to, extent);
            assertEquals(PedestrianRouteResult.Status.FOUND, joined.status());
            assertEquals(from, joined.route().getFirst()); assertEquals(to, joined.route().getLast());
            assertEquals(0, reads.get(), "new endpoint pairing uses the proven upper graph, not a new world flood");
            assertTrue(joined.workUnits() < east.workUnits() / 3);
            KnownPedestrianNavigation.requireRoute(geometry, joined.route());
        }
    }
    @Test void boundedDetailContinuesAlongTheSameKnownItineraryWithoutReexpandingTheWholeTrip() {
        var geometry = geometry(32768, -1, PedestrianGeometryRevision.initial());
        var start = SurfaceAnchor.at(1, 63, 1); var target = SurfaceAnchor.at(24000, 63, 1);
        var extent = PedestrianRouteExtent.MOVEMENT_SEGMENT;
        try (var planner = new CooperativePedestrianPlanner()) {
            var first = finish(planner, geometry, start, target, extent);
            assertEquals(PedestrianRouteResult.Status.SEGMENT_READY, first.status());
            assertEquals(extent.maximumSurfaces(), first.route().size());
            assertNotEquals(target, first.route().getLast());
            KnownPedestrianNavigation.requireRoute(geometry, first.route());
            assertTrue(planner.peek(geometry, start, target).isEmpty(), "partial detail cannot satisfy a complete-route consumer");
            var next = first;
            for (int leg = 0; leg < 8 && next.status() == PedestrianRouteResult.Status.SEGMENT_READY; leg++) {
                var departure = next.route().getLast();
                next = finish(planner, geometry, departure, target, extent);
                assertEquals(departure, next.route().getFirst());
                KnownPedestrianNavigation.requireRoute(geometry, next.route());
                assertTrue(next.workUnits() < first.workUnits() / 3, "continuation reuses the coarse itinerary");
            }
            assertEquals(PedestrianRouteResult.Status.FOUND, next.status());
            assertEquals(target, next.route().getLast());
        }
    }

    @Test void farObstructionInvalidatesTheItineraryAndCannotBecomeAFalsePartialSuccess() {
        var revision = PedestrianGeometryRevision.initial();
        var open = geometry(512, -1, revision);
        var start = SurfaceAnchor.at(1, 63, 1); var target = SurfaceAnchor.at(480, 63, 1);
        var extent = new PedestrianRouteExtent(64);
        try (var planner = new CooperativePedestrianPlanner()) {
            var first = finish(planner, open, start, target, extent);
            assertEquals(PedestrianRouteResult.Status.SEGMENT_READY, first.status());
            var blocked = geometry(512, 400, revision.changed(java.util.Set.of(new PedestrianGeometryRevision.Region(25, 0))));
            assertTrue(planner.peek(blocked, start, target, extent).isEmpty());
            assertEquals(PedestrianRouteResult.Status.NO_PATH, finish(planner, blocked, first.route().getLast(), target, extent).status());
        }
    }

    @Test void simultaneousQueriesShareANewlyProvenTrunkAndKeepExactDetailAddresses() {
        var geometry = geometry(1024, -1, PedestrianGeometryRevision.initial());
        var start = SurfaceAnchor.at(1, 63, 1); var neighbour = SurfaceAnchor.at(2, 63, 2);
        var target = SurfaceAnchor.at(960, 63, 1); var extent = new PedestrianRouteExtent(32);
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(geometry, start, target, extent);
            planner.query(geometry, neighbour, target);
            var bounded = finish(planner, geometry, start, target, extent);
            var complete = finish(planner, geometry, neighbour, target, PedestrianRouteExtent.COMPLETE);
            assertEquals(PedestrianRouteResult.Status.SEGMENT_READY, bounded.status());
            assertEquals(PedestrianRouteResult.Status.FOUND, complete.status());
            KnownPedestrianNavigation.requireRoute(geometry, complete.route());
            assertEquals(complete.route().size(), new java.util.HashSet<>(complete.route()).size(), "shared joins must not add geometric loops");
            var changes = planner.drainChanges();
            assertTrue(changes.contains(new PedestrianPlanningChange(PedestrianRouteRequest.of(geometry, start, target, extent),
                    PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
            assertFalse(changes.contains(new PedestrianPlanningChange(PedestrianRouteRequest.of(geometry, start, target),
                    PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
        }
    }

    private static PedestrianRouteGeometry geometry(int width, int wallX, PedestrianGeometryRevision revision) {
        return new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, width, 16); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return surface.x() == wallX; }
        };
    }
    private static PedestrianRouteResult finish(CooperativePedestrianPlanner planner, PedestrianRouteGeometry geometry,
                                                SurfaceAnchor start, SurfaceAnchor target, PedestrianRouteExtent extent) {
        var result = planner.query(geometry, start, target, extent);
        for (int turn = 0; turn < 4096 && result.status() == PedestrianRouteResult.Status.PLANNING; turn++) {
            planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            result = planner.query(geometry, start, target, extent);
        }
        assertNotEquals(PedestrianRouteResult.Status.PLANNING, result.status()); return result;
    }
}
