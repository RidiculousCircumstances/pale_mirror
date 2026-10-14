package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.util.*;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianWorkerPlanningTest {
    private static final int BUDGET = HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2;
    private static final class ManualWorkers implements Executor {
        final List<Runnable> queue = new ArrayList<>();
        boolean calculating;
        public void execute(Runnable action) { queue.add(action); }
        void run(int index) {
            var action = queue.remove(index); calculating = true;
            try { action.run(); } finally { calculating = false; }
        }
        void drain() { while (!queue.isEmpty()) run(0); }
    }

    @Test void independentRegionsActuallyBuildConcurrentlyRatherThanUnderOneGlobalCacheLock() throws Exception {
        var cache = new PedestrianRegionCache(); var revision = PedestrianGeometryRevision.initial();
        var entered = new java.util.concurrent.CountDownLatch(2);
        var firstColumns = java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
        var geometry = new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 32, 16); }
            public SurfaceAnchor supportAt(int x, int z) {
                if (firstColumns.add(Math.floorDiv(x, 16))) {
                    entered.countDown();
                    try { assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS), "another region must enter while this survey is running"); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
                }
                return SurfaceAnchor.at(x, 63, z);
            }
            public boolean blocked(SurfaceAnchor surface) { return false; }
        };
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> cache.region(geometry, new PedestrianRegion.Tile(0, 0), BUDGET));
            var second = executor.submit(() -> cache.region(geometry, new PedestrianRegion.Tile(1, 0), BUDGET));
            assertNotNull(first.get(10, java.util.concurrent.TimeUnit.SECONDS).region());
            assertNotNull(second.get(10, java.util.concurrent.TimeUnit.SECONDS).region());
        }
    }

    @Test void hostNeverSurveysAndOutOfOrderWorkersPublishInSubmissionOrder() {
        var workers = new ManualWorkers();
        var geometry = geometry(workers, PedestrianGeometryRevision.initial(), -1);
        var a = SurfaceAnchor.at(1, 63, 1); var b = SurfaceAnchor.at(2, 63, 1); var goal = SurfaceAnchor.at(8, 63, 1);
        try (var planner = new CooperativePedestrianPlanner(workers)) {
            planner.query(geometry, a, goal); planner.query(geometry, b, goal);
            planner.advance(BUDGET); assertEquals(2, workers.queue.size());
            workers.run(1); planner.advance(BUDGET);
            assertTrue(planner.drainChanges().isEmpty(), "second worker cannot publish ahead of its submitted predecessor");
            assertEquals(PedestrianRouteResult.Status.PLANNING, planner.peek(geometry, b, goal).orElseThrow().status());
            workers.run(0); planner.advance(BUDGET);
            var changes = planner.drainChanges();
            assertEquals(List.of(PedestrianRouteRequest.of(geometry, a, goal), PedestrianRouteRequest.of(geometry, b, goal)),
                    changes.stream().map(PedestrianPlanningChange::request).toList());
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.query(geometry, a, goal).status());
            assertEquals(0, planner.inFlightCount());
        }
    }

    @Test void changedDependencyDiscardsWorkerResultButUnrelatedRevisionRetainsIt() {
        for (boolean changedRoute : List.of(false, true)) {
            var workers = new ManualWorkers(); var revision = PedestrianGeometryRevision.initial();
            var old = geometry(workers, revision, -1);
            var start = SurfaceAnchor.at(1, 63, 1); var goal = SurfaceAnchor.at(8, 63, 1);
            try (var planner = new CooperativePedestrianPlanner(workers)) {
                planner.query(old, start, goal); planner.advance(BUDGET);
                var current = geometry(workers, revision.changed(Set.of(new PedestrianGeometryRevision.Region(changedRoute ? 0 : 3, 0))),
                        changedRoute ? 4 : -1);
                assertEquals(PedestrianRouteResult.Status.PLANNING, planner.query(current, start, goal).status());
                workers.drain(); planner.advance(BUDGET);
                var result = planner.query(current, start, goal);
                if (!changedRoute) assertEquals(PedestrianRouteResult.Status.FOUND, result.status());
                else {
                    assertEquals(PedestrianRouteResult.Status.PLANNING, result.status(), "old open route must not be published");
                    for (int i = 0; i < 512 && result.status() == PedestrianRouteResult.Status.PLANNING; i++) {
                        workers.drain(); planner.advance(BUDGET); result = planner.query(current, start, goal);
                    }
                    assertEquals(PedestrianRouteResult.Status.NO_PATH, result.status());
                }
            }
        }
    }

    @Test void shutdownRejectsLateResultsWithoutWaitingForTheWorkerOrLosingABound() {
        var workers = new ManualWorkers(); var geometry = geometry(workers, PedestrianGeometryRevision.initial(), -1);
        var planner = new CooperativePedestrianPlanner(workers);
        var start = SurfaceAnchor.at(1, 63, 1); var goal = SurfaceAnchor.at(60, 63, 1);
        for (int i = 0; i < 32; i++) planner.query(geometry, SurfaceAnchor.at(1, 63, i % 16), goal);
        for (int i = 0; i < 32; i++) planner.advance(BUDGET);
        assertTrue(workers.queue.size() <= 8); assertTrue(planner.pendingCount() <= 8);
        planner.close(); workers.drain();
        assertTrue(planner.drainChanges().isEmpty());
        assertEquals(0, planner.pendingCount());
        assertThrows(IllegalStateException.class, () -> planner.query(geometry, start, goal));
    }

    @Test void refreshingGeometryCannotMoveAnOlderCapacityWaitBehindAnotherScope() {
        var workers = new ManualWorkers();
        var occupied = geometry(workers, PedestrianGeometryRevision.initial(), -1);
        var revision = PedestrianGeometryRevision.initial();
        var older = geometry(workers, revision, -1);
        var newer = geometry(workers, PedestrianGeometryRevision.initial(), -1);
        var start = SurfaceAnchor.at(1, 63, 1); var goal = SurfaceAnchor.at(12, 63, 1);
        try (var planner = new CooperativePedestrianPlanner(workers)) {
            for (int i = 0; i < 8; i++) planner.query(occupied, start, SurfaceAnchor.at(3 + i, 63, 1));
            assertEquals("PLANNING_QUEUE_CAPACITY", planner.query(older, start, goal).reason());
            assertEquals("PLANNING_QUEUE_CAPACITY", planner.query(newer, start, goal).reason());
            var refreshed = geometry(workers, revision.changed(Set.of(new PedestrianGeometryRevision.Region(3, 0))), -1);
            planner.query(refreshed, start, goal);
            planner.advance(BUDGET); workers.run(0); planner.advance(BUDGET);
            assertEquals("PLANNING_QUEUED", planner.query(refreshed, start, goal).reason(),
                    "replacing immutable geometry must preserve admission order across task scopes");
            assertEquals("PLANNING_QUEUE_CAPACITY", planner.query(newer, start, goal).reason());
        }
    }

    private static PedestrianRouteGeometry geometry(ManualWorkers workers, PedestrianGeometryRevision revision, int wallX) {
        return new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 64, 16); }
            public SurfaceAnchor supportAt(int x, int z) {
                assertTrue(workers.calculating, "server thread must never survey geometry");
                return SurfaceAnchor.at(x, 63, z);
            }
            public boolean blocked(SurfaceAnchor surface) { return surface.x() == wallX; }
        };
    }
}
