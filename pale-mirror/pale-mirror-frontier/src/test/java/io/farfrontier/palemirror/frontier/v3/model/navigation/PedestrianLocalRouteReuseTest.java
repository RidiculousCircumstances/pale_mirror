package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianLocalRouteReuseTest {
    private static final SurfaceAnchor START = SurfaceAnchor.at(1, 63, 1), GOAL = SurfaceAnchor.at(5, 63, 1);
    private static PedestrianRouteGeometry ground(PedestrianGeometryRevision revision, boolean blocked, boolean unknown, boolean forwardOnly) {
        return new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 8, 4); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor point) { return blocked && point.x() == 3; }
            public boolean clearanceKnown(SurfaceAnchor point) { return !unknown || point.x() != 3; }
            public boolean canTraverse(SurfaceAnchor from, SurfaceAnchor to) {
                return (!forwardOnly || to.x() >= from.x()) && PedestrianRouteGeometry.super.canTraverse(from, to);
            }
        };
    }
    private static void finish(CooperativePedestrianPlanner planner) {
        for (int n = 0; n < 100 && planner.pendingCount() > 0; n++) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
        assertEquals(0, planner.pendingCount());
    }
    @Test void liveAndDetachedRoundTripReuseTheActualLocalProofWithoutAnotherSearch() throws Exception {
        var revision = PedestrianGeometryRevision.initial("local-round-trip");
        var original = ground(revision, false, false, false);
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(original, START, GOAL); finish(planner);
            var route = planner.peek(original, START, GOAL).orElseThrow().route();
            var inverse = new ArrayList<>(route); Collections.reverse(inverse);
            var view = planner.readView();
            assertEquals(inverse, view.query(original, GOAL, START).route());
            assertEquals(inverse, planner.query(original, GOAL, START).route());
            assertEquals(0, planner.pendingCount(), "one completed leg supplies a checked local round-trip in the same advice");
            var changed = revision.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0)));
            assertTrue(view.peek(ground(changed, true, false, false), GOAL, START).isEmpty(), "a changed block invalidates the reverse candidate");
            assertTrue(view.peek(ground(changed, false, true, false), GOAL, START).isEmpty(), "missing clearance cannot become a route");
            var directed = ground(revision, false, false, true);
            assertTrue(view.peek(directed, GOAL, START).isEmpty(), "forward permission grants no reverse edge");
            planner.query(directed, GOAL, START); finish(planner);
            assertEquals(PedestrianRouteResult.Status.NO_PATH, planner.peek(directed, GOAL, START).orElseThrow().status());
            assertTrue(view.peek(ground(PedestrianGeometryRevision.initial("local-round-trip"), false, false, false), GOAL, START).isEmpty(),
                    "another world epoch cannot borrow the proof despite equal scope labels");
        }
    }
    @Test void bodyEnvelopesRemainSeparateEvenWhenTheUnprofiledGroundMatches() throws Exception {
        var ground = ground(PedestrianGeometryRevision.initial("profiled-round-trip"), false, false, false);
        var resident = new ProfiledPedestrianGeometry(ground, UnitNavigationProfile.VILLAGER, ignored -> false);
        var donkey = new ProfiledPedestrianGeometry(ground, UnitNavigationProfile.DONKEY, ignored -> false);
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(resident, START, GOAL); finish(planner);
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.peek(resident, START, GOAL).orElseThrow().status());
            assertTrue(planner.readView().peek(donkey, GOAL, START).isEmpty(), "a resident proof is not a wider body's result");
        }
    }
    @Test void capturedResultsSurviveQueueMutationButFreshViewsReflectRegionalInvalidationAndShutdown() {
        var revision = PedestrianGeometryRevision.initial("read-publication");
        var original = ground(revision, false, false, false);
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(original, START, GOAL); finish(planner);
            var predecessor = planner.readView();
            var changed = ground(revision.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0))), true, false, false);
            planner.query(changed, START, GOAL);
            assertTrue(planner.readView().peek(changed, START, GOAL).isEmpty(), "invalidated completed rows are absent before replanning finishes");
            finish(planner);
            assertEquals(PedestrianRouteResult.Status.NO_PATH, planner.readView().query(changed, START, GOAL).status());
            assertEquals(PedestrianRouteResult.Status.FOUND, predecessor.query(original, START, GOAL).status());
            planner.close();
            assertTrue(planner.readView().peek(changed, START, GOAL).isEmpty(), "closed owner publishes no retained results");
            assertEquals(PedestrianRouteResult.Status.FOUND, predecessor.query(original, START, GOAL).status(), "closing the live queue cannot mutate a frozen read");
        }
    }
}
