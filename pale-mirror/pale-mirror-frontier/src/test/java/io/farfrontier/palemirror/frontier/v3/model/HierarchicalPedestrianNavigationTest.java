package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

class HierarchicalPedestrianNavigationTest {
    @Test void isolatedGoalAcrossRegionBoundariesIsProvedWithoutFloodingTheLargeSourceComponent() {
        var start = SurfaceAnchor.at(1, 63, 1); var goal = SurfaceAnchor.at(32, 63, 16);
        var closed = geometry(new WorldBounds(0, 0, 1024, 1024), (x, z) -> SurfaceAnchor.at(x, 63, z),
                s -> (s.x() == 28 || s.x() == 36) && s.z() >= 12 && s.z() <= 20
                        || (s.z() == 12 || s.z() == 20) && s.x() >= 28 && s.x() <= 36);
        var result = finish(new HierarchicalPedestrianSearch(closed, start, goal));
        assertEquals(PedestrianRouteResult.Status.NO_PATH, result.status());
        assertTrue(result.workUnits() < 100_000, "small disconnected target spans four regions, not the whole world");
        var reverse = finish(new HierarchicalPedestrianSearch(closed, goal, start));
        assertEquals(PedestrianRouteResult.Status.NO_PATH, reverse.status());
    }
    @Test void rebuildingAnUnchangedViewPreservesRotationUnderTheHostYieldFence() {
        var revision = PedestrianGeometryRevision.initial();
        java.util.function.Supplier<PedestrianRouteGeometry> rebuild = () -> new PedestrianRouteGeometry() {
            public WorldBounds bounds() { return new WorldBounds(0, 0, 3_200, 32); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
            public Object version() { return revision; }
            public java.util.Optional<PedestrianGeometryRevision> regionalRevision() { return java.util.Optional.of(revision); }
        };
        var start = SurfaceAnchor.at(1, 63, 1); var near = SurfaceAnchor.at(3, 63, 1);
        var far = SurfaceAnchor.at(2_000, 63, 1); var farther = SurfaceAnchor.at(2_100, 63, 1);
        try (var planner = new CooperativePedestrianPlanner()) {
            var ground = rebuild.get();
            planner.query(ground, start, far); planner.query(ground, start, farther); planner.query(ground, start, near);
            for (int turn = 0; turn < 32 && planner.peek(ground, start, near).orElseThrow().status() == PedestrianRouteResult.Status.PLANNING; turn++) {
                ground = rebuild.get();
                planner.query(ground, start, far); planner.query(ground, start, farther); planner.query(ground, start, near);
                var admission = new java.util.concurrent.atomic.AtomicInteger();
                planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2, () -> admission.incrementAndGet() < 200);
            }
            var result = planner.peek(ground, start, near).orElseThrow();
            assertEquals(PedestrianRouteResult.Status.FOUND, result.status(), "rebase must not repeatedly promote the first long calculation");
            KnownPedestrianNavigation.requireRoute(ground, result.route());
            assertEquals(2, planner.pendingCount());
            assertTrue(planner.waitingDiagnostics(8).stream().allMatch(wait -> wait.searchWorkUnits() > 0));
        }
    }
    @Test void aFullyKnownIsolatedNearbyGoalDoesNotFloodTheWholeWorldAndOpeningItInvalidatesTheFailure() {
        var start = SurfaceAnchor.at(1, 63, 1); var goal = SurfaceAnchor.at(18, 63, 18);
        var closed = geometry(new WorldBounds(0, 0, 1024, 1024), (x, z) -> SurfaceAnchor.at(x, 63, z),
                surface -> Math.abs(surface.x() - goal.x()) + Math.abs(surface.z() - goal.z()) == 1);
        var result = finish(new HierarchicalPedestrianSearch(closed, start, goal));
        assertEquals(PedestrianRouteResult.Status.NO_PATH, result.status());
        assertTrue(result.workUnits() < 100_000, "one known isolated target cannot force a million-column census");
        assertTrue(result.regions() <= 6);
        var open = geometry(closed.bounds(), (x, z) -> SurfaceAnchor.at(x, 63, z), surface -> false);
        var route = finish(new HierarchicalPedestrianSearch(open, start, goal));
        assertEquals(PedestrianRouteResult.Status.FOUND, route.status());
        KnownPedestrianNavigation.requireRoute(open, route.route());
    }
    @Test void longDistanceCapacityCannotExcludeAShortGoalAndYieldStillPreservesAllLongSearches() {
        var ground = geometry(new WorldBounds(0, 0, 3_200, 32), (x, z) -> SurfaceAnchor.at(x, 63, z), s -> false);
        var start = SurfaceAnchor.at(1, 63, 1); var near = SurfaceAnchor.at(3, 63, 1);
        try (var planner = new CooperativePedestrianPlanner()) {
            for (int i = 0; i < 8; i++) planner.query(ground, start, SurfaceAnchor.at(2_000 + i, 63, 1));
            assertEquals(7, planner.pendingCount()); assertEquals(1, planner.deferredCount());
            assertEquals("PLANNING_QUEUED", planner.query(ground, start, near).reason());
            for (int turn = 0; turn < 32 && planner.peek(ground, start, near).orElseThrow().status() == PedestrianRouteResult.Status.PLANNING; turn++) {
                var admission = new java.util.concurrent.atomic.AtomicInteger();
                planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2, () -> admission.incrementAndGet() < 200);
            }
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.peek(ground, start, near).orElseThrow().status());
            assertEquals(8, planner.pendingCount() + planner.deferredCount(), "short completion cannot erase far requests");
            KnownPedestrianNavigation.requireRoute(ground, planner.peek(ground, start, near).orElseThrow().route());
        }
    }
    @Test void unsuccessfulLocalProofStillFindsADistantDetourWithTheFullRegionalSearch() {
        var ground = geometry(new WorldBounds(0, 0, 32, 96), (x, z) -> SurfaceAnchor.at(x, 63, z),
                surface -> surface.x() == 8 && surface.z() < 80);
        var start = SurfaceAnchor.at(2, 63, 2); var target = SurfaceAnchor.at(25, 63, 2);
        var result = finish(new HierarchicalPedestrianSearch(ground, start, target));
        assertEquals(PedestrianRouteResult.Status.FOUND, result.status());
        assertTrue(result.route().stream().anyMatch(surface -> surface.z() >= 80));
        KnownPedestrianNavigation.requireRoute(ground, result.route());
    }
    @Test void selectingACandidateAtTheExactBudgetBoundaryDefersExpansionWithoutLosingTheRoute() {
        var ground = geometry(new WorldBounds(0, 0, 256, 256), (x, z) -> SurfaceAnchor.at(x, 63, z), s -> false);
        var start = SurfaceAnchor.at(2, 63, 2); var target = SurfaceAnchor.at(250, 63, 250);
        // This slice reproduces the old 12,105-unit expansion in a 12,104-unit grant.
        int budget = 12_104;
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(ground, start, target);
            for (int turn = 0; turn < 200 && planner.pendingCount() > 0; turn++) {
                long before = planner.workUnits();
                assertDoesNotThrow(() -> planner.advance(budget));
                assertTrue(planner.workUnits() - before <= budget);
            }
            var result = planner.peek(ground, start, target).orElseThrow();
            assertEquals(PedestrianRouteResult.Status.FOUND, result.status());
            assertEquals(start, result.route().getFirst());
            assertEquals(target, result.route().getLast());
            KnownPedestrianNavigation.requireRoute(ground, result.route());
        }
    }

    @Test void aLowCeilingInvalidatesAStepEvenWhenBothStationsAreClear() {
        var bootstrap = FrontierBootstrapper.create(new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:step-clearance"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r7"));
        var low = SurfaceAnchor.at(10, 63, 10); var high = SurfaceAnchor.at(11, 64, 10);
        var ceiling = new BlockPosition(10, 66, 10);
        var blocked = KnownPedestrianNavigation.geometry(bootstrap, java.util.Set.of(ceiling),
                (x, z) -> x == 10 && z == 10 ? low : x == 11 && z == 10 ? high : null);
        assertFalse(blocked.blocked(low)); assertFalse(blocked.blocked(high));
        assertFalse(blocked.canTraverse(low, high)); assertFalse(blocked.canTraverse(high, low));
        assertEquals(PedestrianRouteResult.Status.UNKNOWN_GEOMETRY,
                finish(new HierarchicalPedestrianSearch(blocked, low, high)).status());
        assertThrows(IllegalArgumentException.class, () -> KnownPedestrianNavigation.requireRoute(blocked, List.of(low, high)));
        var clear = KnownPedestrianNavigation.geometry(bootstrap, java.util.Set.of(),
                (x, z) -> x == 10 && z == 10 ? low : x == 11 && z == 10 ? high : null);
        assertEquals(PedestrianRouteResult.Status.FOUND, finish(new HierarchicalPedestrianSearch(clear, low, high)).status());
    }
    @Test void shortSearchesShareOneBudgetAndDeferredWorkKeepsItsAgeAndFinishes() {
        var ground = geometry(new WorldBounds(0, 0, 32, 32), (x, z) -> SurfaceAnchor.at(x, 63, z), s -> false);
        var start = SurfaceAnchor.at(1, 63, 1);
        try (var planner = new CooperativePedestrianPlanner()) {
            for (int i = 0; i < 12; i++) planner.query(ground, start, SurfaceAnchor.at(2 + i, 63, 1));
            assertEquals(8, planner.pendingCount()); assertEquals(4, planner.deferredCount());
            assertEquals(4, planner.waitingDiagnostics(12).stream().filter(
                    CooperativePedestrianPlanner.WaitingDiagnostic::deferred).count());
            int budget = HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2;
            planner.advance(budget);
            assertTrue(planner.completedCount() >= 3, "one short result cannot discard the rest of the shared budget");
            assertTrue(planner.workUnits() <= budget);
            assertEquals(planner.pendingCount() == 0 && planner.deferredCount() == 0 ? 0 : 1, planner.oldestWaitTurns());
            long work = planner.workUnits();
            var waiting = planner.waitingDiagnostics(12);
            for (int read = 0; read < 20; read++) assertEquals(waiting, planner.waitingDiagnostics(12));
            assertEquals(work, planner.workUnits(), "diagnostics must not spend search work or refresh wait age");
            for (int turn = 0; turn < 100 && planner.pendingCount() > 0; turn++) {
                long before = planner.workUnits(); planner.advance(budget);
                assertTrue(planner.workUnits() - before <= budget);
            }
            assertEquals(12, planner.completedCount()); assertEquals(0, planner.deferredCount());
            assertEquals(0, planner.oldestWaitTurns());
            for (int i = 0; i < 12; i++) assertEquals(PedestrianRouteResult.Status.FOUND,
                    planner.peek(ground, start, SurfaceAnchor.at(2 + i, 63, 1)).orElseThrow().status());
        }
    }

    @Test void opportunityInspectionCannotStartSearchOrOwnWakeButDistinguishesUnresolvedFromNoPath() throws Exception {
        var ground = geometry(new WorldBounds(0, 0, 32, 16), (x, z) -> SurfaceAnchor.at(x, 63, z), s -> s.x() == 8);
        var start = SurfaceAnchor.at(2, 63, 2); var goal = SurfaceAnchor.at(25, 63, 2);
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner)) {
            java.util.function.BooleanSupplier probe = () -> {
                var result = PedestrianRoutePlanning.query(ground, start, goal);
                if (result.status() == PedestrianRouteResult.Status.PLANNING)
                    PedestrianRoutePlanning.await(PedestrianRouteRequest.of(ground, start, goal));
                return result.status() == PedestrianRouteResult.Status.FOUND;
            };
            var observation = PedestrianRoutePlanning.observe(() -> PedestrianRoutePlanning.inspectAvailability(probe));
            assertEquals(PedestrianRoutePlanning.Readiness.UNRESOLVED, observation.result());
            assertTrue(observation.requests().isEmpty()); assertEquals(0, planner.pendingCount());
            PedestrianRoutePlanning.query(ground, start, goal);
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertEquals(PedestrianRoutePlanning.Readiness.UNAVAILABLE, PedestrianRoutePlanning.inspectAvailability(probe));
            assertEquals(0, planner.pendingCount());
            assertThrows(IllegalArgumentException.class, () -> PedestrianRoutePlanning.inspectAvailability(() -> {
                throw new IllegalArgumentException("invalid authority is not an unavailable opportunity");
            }));
        }
    }
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
            for (int turn = 0; turn < 100 && planner.peek(ground, start, deferred).orElseThrow().reason().equals("PLANNING_QUEUE_CAPACITY"); turn++)
                planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertTrue(planner.progressRevision() > signal);
            var admitted = planner.query(ground, start, deferred);
            assertTrue(admitted.status() == PedestrianRouteResult.Status.PLANNING || admitted.status() == PedestrianRouteResult.Status.FOUND);
            assertNotEquals("PLANNING_QUEUE_CAPACITY", admitted.reason());
            assertEquals(admitted, planner.peek(ground, start, deferred).orElseThrow());
        }
    }
    @Test void terminalFailureAndGeometryCancellationNameOnlyTheirExactRequests() {
        var bounds = new WorldBounds(0, 0, 32, 16);
        var old = geometry(bounds, (x, z) -> SurfaceAnchor.at(x, 63, z), s -> s.x() == 8);
        var changed = geometry(bounds, (x, z) -> SurfaceAnchor.at(x, 63, z), s -> false);
        var start = SurfaceAnchor.at(2, 63, 2); var goal = SurfaceAnchor.at(25, 63, 2);
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(old, start, goal);
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertEquals(PedestrianRouteResult.Status.NO_PATH, planner.peek(old, start, goal).orElseThrow().status());
            assertEquals(List.of(new PedestrianPlanningChange(PedestrianRouteRequest.of(old, start, goal),
                    PedestrianPlanningChange.Kind.RESULT_AVAILABLE)), planner.drainChanges());
            assertTrue(planner.drainChanges().isEmpty());
            planner.query(changed, start, goal);
            assertEquals(List.of(new PedestrianPlanningChange(PedestrianRouteRequest.of(old, start, goal),
                    PedestrianPlanningChange.Kind.INVALIDATED)), planner.drainChanges());
        }
    }
}
