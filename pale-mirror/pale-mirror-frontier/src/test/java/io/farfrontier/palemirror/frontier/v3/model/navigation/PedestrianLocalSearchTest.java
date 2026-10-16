package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianLocalSearchTest {
    private static PedestrianRouteGeometry pocket(boolean observed, PedestrianGeometryRevision revision) {
        return new PedestrianRouteGeometry() {
            public WorldBounds bounds() { return new WorldBounds(0, 0, 1024, 1024); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 0, z); }
            public Object version() { return revision; }
            public java.util.Optional<PedestrianGeometryRevision> regionalRevision() { return java.util.Optional.of(revision); }
            public boolean clearanceKnown(SurfaceAnchor surface) { return observed || surface.x() != 3 || surface.z() != 1; }
            public boolean blocked(SurfaceAnchor surface) {
                return !clearanceKnown(surface) || surface.x() == 3 && surface.z() <= 3 && surface.z() != 1
                        || surface.z() == 3 && surface.x() <= 3;
            }
        };
    }
    @Test void unknownEntranceToSmallPocketWaitsLocallyAndWakesWhenThatFactArrives() {
        var revision = PedestrianGeometryRevision.initial(); var geometry = pocket(false, revision);
        var start = SurfaceAnchor.at(5, 0, 1); var target = SurfaceAnchor.at(1, 0, 1);
        var missing = new BlockPosition(3, 0, 1);
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(geometry, start, target);
            for (int i = 0; i < 10 && planner.pendingCount() > 0; i++) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK);
            var result = planner.peek(geometry, start, target).orElseThrow();
            assertEquals(PedestrianRouteResult.Status.UNKNOWN_GEOMETRY, result.status());
            assertEquals("TARGET_COMPONENT_NEEDS_GEOMETRY", result.reason());
            assertTrue(result.missingGeometry().contains(missing));
            assertEquals(0, result.regions(), "a finite exhausted local component must not flood 4096 regions");
            assertTrue(result.workUnits() < 40_000); assertEquals(0, planner.pendingCount());
            long spent = planner.workUnits();
            for (int i = 0; i < 20; i++) { planner.query(geometry, start, target); planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK); }
            assertEquals(spent, planner.workUnits(), "unchanged unknown geometry is not recurring search work");
            planner.drainChanges(); planner.geometryObserved(java.util.Set.of(missing));
            assertTrue(planner.drainChanges().stream().anyMatch(change -> change.kind() == PedestrianPlanningChange.Kind.INVALIDATED));
            var current = pocket(true, revision.changed(java.util.Set.of(new PedestrianGeometryRevision.Region(0, 0))));
            planner.query(current, start, target);
            for (int i = 0; i < 10 && planner.pendingCount() > 0; i++) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK);
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.peek(current, start, target).orElseThrow().status());
        }
    }
    @Test void reverseSearchTraversesIncomingEdgesAndReturnsTheOriginalTravelDirection() {
        var geometry = new PedestrianRouteGeometry() {
            public WorldBounds bounds() { return new WorldBounds(0, 0, 5, 1); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 0, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
            public Object version() { return this; }
            public boolean canTraverse(SurfaceAnchor from, SurfaceAnchor to) {
                return to.x() == from.x() + 1 && to.z() == from.z();
            }
        };
        var start = SurfaceAnchor.at(0, 0, 0); var target = SurfaceAnchor.at(4, 0, 0);
        var reverse = new PedestrianLocalSearch(geometry, target, start, ignored -> { }, true);
        reverse.advance(1000, () -> true);
        assertEquals(start, reverse.result().getFirst()); assertEquals(target, reverse.result().getLast());
        for (int i = 1; i < reverse.result().size(); i++)
            assertTrue(geometry.canTraverse(reverse.result().get(i - 1), reverse.result().get(i)));
        var forbidden = new PedestrianLocalSearch(geometry, start, target, ignored -> { }, true);
        forbidden.advance(1000, () -> true);
        assertTrue(forbidden.disconnected()); assertNull(forbidden.result());
    }

    @Test void unknownNeighbourIsNotAProofOfDisconnectedness() {
        var geometry = new PedestrianRouteGeometry() {
            public WorldBounds bounds() { return new WorldBounds(0, 0, 5, 1); }
            public SurfaceAnchor supportAt(int x, int z) { return x == 2 ? null : SurfaceAnchor.at(x, 0, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
            public Object version() { return this; }
        };
        var search = new PedestrianLocalSearch(geometry, SurfaceAnchor.at(0, 0, 0), SurfaceAnchor.at(4, 0, 0), ignored -> { });
        search.advance(1000, () -> true);
        assertTrue(search.finished()); assertFalse(search.disconnected()); assertNull(search.result());
    }
}
