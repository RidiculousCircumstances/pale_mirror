package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

/** The same production planner/profile/receipt boundary must distinguish vertically stacked routes. */
class LayeredPedestrianNavigationTest {
    @Test void hostYieldRetainsRegionConstructionAndFairPeerWithoutReportingNoPath() {
        var map = geometry(new WorldBounds(0, 0, 32, 16),
                (x, z) -> List.of(SurfaceAnchor.at(x, 63, z)), Set.of(), UnitNavigationProfile.VILLAGER);
        var start = SurfaceAnchor.at(1, 63, 1); var target = SurfaceAnchor.at(30, 63, 1);
        var peerStart = SurfaceAnchor.at(20, 63, 5); var peerTarget = SurfaceAnchor.at(21, 63, 5);
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(map, start, target); planner.query(map, peerStart, peerTarget);
            long previous = planner.workUnits();
            var admitted = new java.util.concurrent.atomic.AtomicInteger();
            planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2,
                    () -> admitted.incrementAndGet() <= 40);
            assertTrue(planner.workUnits() > previous);
            assertTrue(planner.workUnits() - previous <= 40, "yield must reach the region builder, not just its outer search");
            assertEquals(2, planner.pendingCount());
            for (int turn = 0; planner.pendingCount() > 0 && turn < 1000; turn++)
                planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.peek(map, start, target).orElseThrow().status());
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.peek(map, peerStart, peerTarget).orElseThrow().status());
            KnownPedestrianNavigation.requireRoute(map, planner.peek(map, start, target).orElseThrow().route());
        }
    }
    private static PedestrianRouteGeometry geometry(WorldBounds bounds,
            BiFunction<Integer, Integer, List<SurfaceAnchor>> levels, Set<BlockPosition> obstacles, UnitNavigationProfile profile) {
        var revision = PedestrianGeometryRevision.initial();
        var raw = new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return bounds; }
            public List<SurfaceAnchor> surfacesAt(int x, int z) { return levels.apply(x, z); }
            public SurfaceAnchor supportAt(int x, int z) { var all = surfacesAt(x, z); return all.isEmpty() ? null : all.getLast(); }
            public boolean supportAvailable(SurfaceAnchor surface) { return knowsSupport(surface) && !obstacles.contains(surface.support()); }
            public boolean blocked(SurfaceAnchor surface) { return !supportAvailable(surface); }
        };
        return new ProfiledPedestrianGeometry(raw, profile, obstacles::contains);
    }
    private static PedestrianRouteResult finish(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        var search = new HierarchicalPedestrianSearch(geometry, start, target);
        PedestrianRouteResult result;
        int turns = 0, budget = HierarchicalPedestrianSearch.MIN_SLICE_WORK;
        do {
            long before = search.workUnits(); result = search.advance(budget);
            assertTrue(search.workUnits() - before <= budget, "layer count cannot exceed the common per-turn budget");
            assertTrue(++turns < 1000, "bounded small fixture cannot remain pending forever");
        } while (result.status() == PedestrianRouteResult.Status.PLANNING);
        return result;
    }
    @Test void tunnelAndBridgeCrossChunkBordersIndependentlyWithoutAnInventedVerticalEdge() {
        var map = geometry(new WorldBounds(0, 0, 48, 1),
                (x, z) -> List.of(SurfaceAnchor.at(x, 58, z), SurfaceAnchor.at(x, 63, z)), Set.of(), UnitNavigationProfile.VILLAGER);
        for (int y : new int[]{58, 63}) {
            var start = SurfaceAnchor.at(1, y, 0); var target = SurfaceAnchor.at(46, y, 0);
            var result = finish(map, start, target);
            assertEquals(PedestrianRouteResult.Status.FOUND, result.status());
            assertTrue(result.route().stream().allMatch(surface -> surface.y() == y));
            KnownPedestrianNavigation.requireRoute(map, result.route());
        }
        assertEquals(PedestrianRouteResult.Status.NO_PATH,
                finish(map, SurfaceAnchor.at(1, 58, 0), SurfaceAnchor.at(1, 63, 0)).status());
        assertEquals(PedestrianRouteResult.Status.UNKNOWN_GEOMETRY,
                finish(map, SurfaceAnchor.at(1, 60, 0), SurfaceAnchor.at(46, 63, 0)).status());
    }
    @Test void onlyRealAscendingSurfacesConnectLevelsAndADamagedStepClosesTheConnection() {
        BiFunction<Integer, Integer, List<SurfaceAnchor>> stairs = (x, z) -> List.of(SurfaceAnchor.at(x, 58 + x, z));
        var bounds = new WorldBounds(0, 0, 6, 1);
        var start = SurfaceAnchor.at(0, 58, 0); var target = SurfaceAnchor.at(5, 63, 0);
        var map = geometry(bounds, stairs, Set.of(), UnitNavigationProfile.VILLAGER);
        var route = finish(map, start, target).route(); assertEquals(6, route.size());
        KnownPedestrianNavigation.requireRoute(map, route);
        var damaged = geometry(bounds, stairs, Set.of(new BlockPosition(3, 62, 0)), UnitNavigationProfile.VILLAGER);
        assertEquals(PedestrianRouteResult.Status.NO_PATH, finish(damaged, start, target).status());
        assertThrows(IllegalArgumentException.class, () -> KnownPedestrianNavigation.requireRoute(damaged, route));
    }
    @Test void upperSupportIsACeilingOnlyWhereItIntersectsTheDeclaredBody() {
        var bounds = new WorldBounds(0, 0, 32, 1);
        BiFunction<Integer, Integer, List<SurfaceAnchor>> lowRoof = (x, z) -> List.of(SurfaceAnchor.at(x, 58, z), SurfaceAnchor.at(x, 60, z));
        var start = SurfaceAnchor.at(1, 58, 0); var target = SurfaceAnchor.at(30, 58, 0);
        var shortBody = new UnitNavigationProfile(600_000, 900_000, 1, 1, Set.of(TraversalCapability.PEDESTRIAN));
        assertEquals(PedestrianRouteResult.Status.FOUND, finish(geometry(bounds, lowRoof, Set.of(), shortBody), start, target).status());
        assertEquals(PedestrianRouteResult.Status.NO_PATH, finish(geometry(bounds, lowRoof, Set.of(), UnitNavigationProfile.VILLAGER), start, target).status());
    }
    @Test void asymmetricDropDoesNotTurnAOneWayEdgeIntoATwoWayComponent() {
        var profile = new UnitNavigationProfile(600_000, 1_950_000, 0, 1, Set.of(TraversalCapability.PEDESTRIAN));
        var map = geometry(new WorldBounds(0, 0, 2, 1), (x, z) -> List.of(SurfaceAnchor.at(x, x == 0 ? 64 : 63, z)), Set.of(), profile);
        var high = SurfaceAnchor.at(0, 64, 0); var low = SurfaceAnchor.at(1, 63, 0);
        assertEquals(PedestrianRouteResult.Status.FOUND, finish(map, high, low).status());
        assertEquals(PedestrianRouteResult.Status.NO_PATH, finish(map, low, high).status());
    }
    @Test void unavailableHistoricalBirthAllowsClearLocalPlacementButNeverWalking() {
        var map = geometry(new WorldBounds(0, 0, 4, 1), (x, z) -> List.of(SurfaceAnchor.at(x, 63, z)),
                Set.of(new BlockPosition(0, 64, 0)), UnitNavigationProfile.VILLAGER);
        var retained = SurfaceAnchor.at(0, 63, 0); var adjacent = SurfaceAnchor.at(1, 63, 0);
        assertTrue(map.canProjectPlacement(retained, adjacent));
        assertFalse(map.canTraverse(retained, adjacent));
        assertFalse(map.canProjectPlacement(retained, SurfaceAnchor.at(3, 63, 0)));
        assertEquals(PedestrianRouteResult.Status.NO_PATH, finish(map, retained, adjacent).status());
    }
    @Test void denseLayeredConstructionYieldsWithoutBlockingAFairPeer() {
        var dense = geometry(new WorldBounds(0, 0, 16, 16), (x, z) -> {
            var levels = new ArrayList<SurfaceAnchor>();
            for (int y = 0; y < 16; y++) levels.add(SurfaceAnchor.at(x, 3 * y, z));
            return List.copyOf(levels);
        }, Set.of(), UnitNavigationProfile.VILLAGER);
        var flat = geometry(new WorldBounds(0, 0, 16, 16), (x, z) -> List.of(SurfaceAnchor.at(x, 63, z)), Set.of(), UnitNavigationProfile.VILLAGER);
        try (var planner = new CooperativePedestrianPlanner()) {
            var denseStart = SurfaceAnchor.at(1, 0, 1); var denseEnd = SurfaceAnchor.at(14, 0, 14);
            var flatStart = SurfaceAnchor.at(1, 63, 1); var flatEnd = SurfaceAnchor.at(2, 63, 1);
            planner.query(dense, denseStart, denseEnd); planner.query(flat, flatStart, flatEnd);
            int budget = HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2;
            for (int turn = 0; turn < 4; turn++) {
                long before = planner.workUnits(); planner.advance(budget); assertTrue(planner.workUnits() - before <= budget);
            }
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.peek(flat, flatStart, flatEnd).orElseThrow().status());
            assertEquals(PedestrianRouteResult.Status.PLANNING, planner.query(dense, denseStart, denseEnd).status());
            for (int turn = 0; planner.pendingCount() > 0 && turn < 1000; turn++) planner.advance(budget);
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.peek(dense, denseStart, denseEnd).orElseThrow().status());
        }
    }
}
