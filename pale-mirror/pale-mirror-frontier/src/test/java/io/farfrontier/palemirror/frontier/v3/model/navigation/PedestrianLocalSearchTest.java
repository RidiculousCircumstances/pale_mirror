package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianLocalSearchTest {
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
