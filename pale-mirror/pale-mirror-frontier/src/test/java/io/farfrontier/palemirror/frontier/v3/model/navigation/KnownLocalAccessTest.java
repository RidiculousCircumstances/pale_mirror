package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnownLocalAccessTest {
    @Test void exhaustedOpportunityReusesOnlyWalkingFactsWhileRecheckingCurrentAvailabilityAndPermission() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var geometry = flat(calls, Set.of()); var start = SurfaceAnchor.at(2, 63, 2);
        var target = SurfaceAnchor.at(3, 63, 2);
        assertTrue(KnownLocalAccess.opportunity(geometry, start, 8, ignored -> false, ignored -> true).isEmpty());
        int searched = calls.get(); assertTrue(searched > 0 && searched <= 4096);
        assertTrue(KnownLocalAccess.opportunity(geometry, start, 8, target::equals, ignored -> false).isEmpty(),
                "changed owner permission is evaluated even when walking geometry is retained");
        var route = KnownLocalAccess.opportunity(geometry, start, 8, target::equals, ignored -> true).orElseThrow().route();
        assertEquals(List.of(start, target), route, "a formerly unavailable destination may become the current positive opportunity");
        assertEquals(searched, calls.get(), "an exhausted optional scan must not redo the same geometry on every resident review");
    }

    @Test void changedImmutableGeometryCannotReuseThePredecessorsExplorationEvenWithAnEqualVersionLabel() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var start = SurfaceAnchor.at(2, 63, 2); var target = SurfaceAnchor.at(3, 63, 2);
        var open = flat(calls, Set.of());
        assertTrue(KnownLocalAccess.opportunity(open, start, 8, ignored -> false, ignored -> true).isEmpty());
        var blocked = flat(calls, Set.of(target));
        assertEquals(open.version(), blocked.version());
        assertTrue(KnownLocalAccess.opportunity(blocked, start, 8, target::equals, ignored -> true).isEmpty(),
                "a version/hash label is not the complete captured geometry");
        assertEquals(target, KnownLocalAccess.opportunity(open, start, 8, target::equals, ignored -> true).orElseThrow().route().getLast(),
                "a successor's obstruction cannot poison its predecessor's immutable facts");
    }

    private static PedestrianRouteGeometry flat(java.util.concurrent.atomic.AtomicInteger calls, Set<SurfaceAnchor> blocked) {
        return new PedestrianRouteGeometry() {
            public Object version() { return "same-label"; }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 8, 8); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return blocked.contains(surface); }
            public boolean canTraverse(SurfaceAnchor from, SurfaceAnchor to) {
                calls.incrementAndGet(); return !blocked(from) && !blocked(to);
            }
        };
    }

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
