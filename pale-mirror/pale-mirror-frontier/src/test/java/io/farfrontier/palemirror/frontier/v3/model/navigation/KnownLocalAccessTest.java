package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnownLocalAccessTest {
    @Test void distantGoalsDoNotReadGeometryAndAnExhaustedShortcutLeavesCompleteRoutingAvailable() {
        var calls = new int[1];
        var map = new PedestrianRouteGeometry() {
            public WorldBounds bounds() { return new WorldBounds(-64, -64, 128, 128); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
            public boolean canTraverse(SurfaceAnchor from, SurfaceAnchor to) { calls[0]++; return true; }
        };
        var start = SurfaceAnchor.at(0, 63, 0);
        assertTrue(KnownLocalAccess.nearest(map, start, Set.of(SurfaceAnchor.at(173, 63, 0)), 16).isEmpty());
        assertEquals(0, calls[0]);
        var nearby = SurfaceAnchor.at(1, 63, 0);
        assertEquals(nearby, KnownLocalAccess.nearest(map, start, Set.of(nearby), 16).orElseThrow().route().getLast());
        calls[0] = 0;
        var farther = SurfaceAnchor.at(16, 63, 16);
        assertTrue(KnownLocalAccess.nearest(map, start, Set.of(farther), 16).isEmpty());
        assertTrue(calls[0] <= 128);
        var complete = PedestrianRoutePlanning.calculate(map, start, farther);
        assertEquals(PedestrianRouteResult.Status.FOUND, complete.status());
        KnownPedestrianNavigation.requireRoute(map, complete.route());
    }
}
