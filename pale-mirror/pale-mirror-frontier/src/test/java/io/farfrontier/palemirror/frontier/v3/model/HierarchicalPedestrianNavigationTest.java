package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

class HierarchicalPedestrianNavigationTest {
    private static PedestrianRouteGeometry geometry(WorldBounds bounds, BiFunction<Integer, Integer, SurfaceAnchor> supports,
                                                    Predicate<SurfaceAnchor> blocked) {
        return new PedestrianRouteGeometry() {
            public WorldBounds bounds() { return bounds; }
            public SurfaceAnchor supportAt(int x, int z) { return supports.apply(x, z); }
            public boolean blocked(SurfaceAnchor surface) { return blocked.test(surface); }
        };
    }
    private static PedestrianRouteResult finish(HierarchicalPedestrianSearch search) {
        PedestrianRouteResult result;
        do {
            long before = search.workUnits();
            result = search.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertTrue(result.workUnits() - before <= HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
        } while (result.status() == PedestrianRouteResult.Status.PLANNING);
        return result;
    }
    @Test void thousandsOfBlocksUseConnectedRegionsWithBoundedWorkPerSlice() {
        var ground = geometry(new WorldBounds(-64, -16, 3_200, 32), (x, z) -> SurfaceAnchor.at(x, 63, z), s -> false);
        var start = SurfaceAnchor.at(-33, 63, 0); var goal = SurfaceAnchor.at(3_000, 63, 0);
        var search = new HierarchicalPedestrianSearch(ground, start, goal);
        var result = finish(search);
        assertEquals(PedestrianRouteResult.Status.FOUND, result.status());
        assertEquals(start, result.route().getFirst()); assertEquals(goal, result.route().getLast());
        assertTrue(result.regions() < 500, "macro search should not visit every world column");
        assertDoesNotThrow(() -> new PedestrianRouteReceipt(result.route()));
    }
    @Test void nonFlatPassageMustActuallyConnectAcrossAndWithinRegions() {
        var ground = geometry(new WorldBounds(0, 0, 64, 64), (x, z) -> SurfaceAnchor.at(x, 63 + x / 16, z),
                s -> s.x() == 31 && s.z() != 47);
        var result = finish(new HierarchicalPedestrianSearch(ground, SurfaceAnchor.at(4, 63, 4), SurfaceAnchor.at(60, 66, 4)));
        assertEquals(PedestrianRouteResult.Status.FOUND, result.status());
        assertTrue(result.route().contains(SurfaceAnchor.at(31, 64, 47)));
        KnownPedestrianNavigation.requireRoute(ground, result.route());
        assertDoesNotThrow(() -> new PedestrianRouteReceipt(result.route()));
    }
    @Test void disconnectedLocalComponentsCannotBeBridgedByTileAdjacency() {
        var ground = geometry(new WorldBounds(0, 0, 32, 16), (x, z) -> SurfaceAnchor.at(x, 63, z), s -> s.x() == 8);
        var result = finish(new HierarchicalPedestrianSearch(ground, SurfaceAnchor.at(2, 63, 2), SurfaceAnchor.at(25, 63, 2)));
        assertEquals(PedestrianRouteResult.Status.NO_PATH, result.status()); assertTrue(result.route().isEmpty());
    }
    @Test void unknownIsNotOpenAndCachedFailureDoesNotRepeatSearch() {
        var bounds = new WorldBounds(0, 0, 32, 16);
        var unknown = geometry(bounds, (x, z) -> x == 8 ? null : SurfaceAnchor.at(x, 63, z), s -> false);
        var start = SurfaceAnchor.at(2, 63, 2); var end = SurfaceAnchor.at(25, 63, 2);
        try (var planner = new CooperativePedestrianPlanner()) {
            assertEquals(PedestrianRouteResult.Status.PLANNING, planner.query(unknown, start, end).status());
            long planningRevision = planner.progressRevision();
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertTrue(planner.progressRevision() > planningRevision);
            assertEquals(PedestrianRouteResult.Status.UNKNOWN_GEOMETRY, planner.peek(unknown, start, end).orElseThrow().status());
            long cost = planner.workUnits();
            for (int i = 0; i < 100; i++) assertEquals(PedestrianRouteResult.Status.UNKNOWN_GEOMETRY, planner.query(unknown, start, end).status());
            assertEquals(cost, planner.workUnits());
            var changed = geometry(bounds, (x, z) -> SurfaceAnchor.at(x, 63, z), s -> false);
            assertTrue(planner.peek(changed, start, end).isEmpty(), "obsolete geometry is not current evidence");
            assertEquals(PedestrianRouteResult.Status.PLANNING, planner.query(changed, start, end).status());
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.query(changed, start, end).status());
        }
    }
    @Test void queuedLongSearchDoesNotMonopolizeOtherRequestsOrPretendArrival() {
        var ground = geometry(new WorldBounds(0, 0, 3_200, 32), (x, z) -> SurfaceAnchor.at(x, 63, z), s -> false);
        var start = SurfaceAnchor.at(1, 63, 1); var far = SurfaceAnchor.at(3_000, 63, 1); var near = SurfaceAnchor.at(4, 63, 1);
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(ground, start, far); planner.query(ground, start, near);
            for (int i = 0; i < 4; i++) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.query(ground, start, near).status());
            assertEquals(PedestrianRouteResult.Status.PLANNING, planner.query(ground, start, far).status());
        }
    }
    @Test void changedGeometryCancelsObsoletePendingWorkInsteadOfFillingTheQueue() {
        var bounds = new WorldBounds(0, 0, 3_200, 32);
        var old = geometry(bounds, (x, z) -> SurfaceAnchor.at(x, 63, z), s -> false);
        var changed = geometry(bounds, (x, z) -> SurfaceAnchor.at(x, 63, z), s -> s.x() == 3);
        try (var planner = new CooperativePedestrianPlanner()) {
            var start = SurfaceAnchor.at(1, 63, 1);
            planner.query(old, start, SurfaceAnchor.at(3_000, 63, 1));
            planner.query(changed, start, SurfaceAnchor.at(4, 63, 1));
            assertEquals(1, planner.pendingCount());
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertEquals(PedestrianRouteResult.Status.NO_PATH, planner.query(changed, start, SurfaceAnchor.at(4, 63, 1)).status());
            assertEquals(PedestrianRouteResult.Status.NO_PATH, planner.peek(changed, start, SurfaceAnchor.at(4, 63, 1)).orElseThrow().status());
        }
    }
    @Test void queueCapacityWaitRemainsVisibleAndCanRetryWhenCalculationFreesASlot() {
        var ground = geometry(new WorldBounds(0, 0, 32, 32), (x, z) -> SurfaceAnchor.at(x, 63, z), s -> false);
        var start = SurfaceAnchor.at(1, 63, 1); var deferred = SurfaceAnchor.at(12, 63, 1);
        try (var planner = new CooperativePedestrianPlanner()) {
            for (int i = 0; i < 8; i++) planner.query(ground, start, SurfaceAnchor.at(3 + i, 63, 1));
            assertEquals("PLANNING_QUEUE_CAPACITY", planner.query(ground, start, deferred).reason());
            assertEquals("PLANNING_QUEUE_CAPACITY", planner.peek(ground, start, deferred).orElseThrow().reason());
            long signal = planner.progressRevision();
            for (int turn = 0; turn < 100 && planner.pendingCount() == 8; turn++)
                planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertTrue(planner.pendingCount() < 8); assertTrue(planner.progressRevision() > signal);
            assertEquals("PLANNING_QUEUED", planner.query(ground, start, deferred).reason());
            assertEquals("PLANNING_QUEUED", planner.peek(ground, start, deferred).orElseThrow().reason());
        }
    }
}
