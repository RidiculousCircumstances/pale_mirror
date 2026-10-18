package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianRegionalInvalidationTest {
    @Test void directReplayReusesReconstructedExactKnowledgeButNotChangedPermissionsOrBounds() {
        var revision = PedestrianGeometryRevision.initial(); var bounds = new WorldBounds(0, 0, 32, 16);
        var start = SurfaceAnchor.at(1, 63, 1); var end = SurfaceAnchor.at(10, 63, 1);
        var first = PedestrianRoutePlanning.calculate(flat(revision, bounds, s -> false), start, end);
        assertEquals(PedestrianRouteResult.Status.FOUND, first.status());
        var equal = new PedestrianGeometryRevision(revision.scope(), revision.geometryScope(), revision.baseline(), revision.revisions());
        var reads = new AtomicInteger();
        var reconstructed = geometry(equal, bounds, (x, z) -> {
            reads.incrementAndGet(); return SurfaceAnchor.at(x, 63, z);
        }, s -> false);
        assertSame(first, PedestrianRoutePlanning.calculate(reconstructed, start, end));
        assertEquals(0, reads.get(), "an identical query must not restart search or survey");
        var changed = revision.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0)));
        assertEquals(PedestrianRouteResult.Status.NO_PATH,
                PedestrianRoutePlanning.calculate(flat(changed, bounds, s -> s.x() == 8), start, end).status());
        var reopened = changed.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0)));
        assertEquals(PedestrianRouteResult.Status.FOUND,
                PedestrianRoutePlanning.calculate(flat(reopened, bounds, s -> false), start, end).status());
        assertEquals(PedestrianRouteResult.Status.NO_PATH, PedestrianRoutePlanning.calculate(
                flat(revision, new WorldBounds(0, 0, 8, 16), s -> false), start, end).status());
        assertEquals(PedestrianRouteResult.Status.NO_PATH, PedestrianRoutePlanning.calculate(
                flat(PedestrianGeometryRevision.initial(), bounds, s -> s.x() == 8), start, end).status());
    }
    @Test void completedEstimateRetainsItsConnectedItineraryNotTheExploredAlternatives() {
        var revision = PedestrianGeometryRevision.initial(); var bounds = new WorldBounds(0, 0, 256, 256);
        var start = SurfaceAnchor.at(1, 63, 1); var target = SurfaceAnchor.at(240, 63, 240);
        var geometry = flat(revision, bounds, s -> false);
        var search = new HierarchicalPedestrianSearch(geometry, start, target, new PedestrianRegionCache(), null,
                PedestrianRouteExtent.ESTIMATE, new PedestrianItineraryCache());
        PedestrianRouteResult result = null;
        for (int i = 0; i < 4096; i++) {
            result = search.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            if (result.status() != PedestrianRouteResult.Status.PLANNING) break;
        }
        assertEquals(PedestrianRouteResult.Status.ESTIMATED, result.status());
        var proof = search.dependencies();
        assertTrue(proof.revisions().size() < result.regions(), "completed proof must not retain explored dead alternatives");
        assertTrue(proof.revisions().containsKey(PedestrianRegion.Tile.at(start)));
        assertTrue(proof.revisions().containsKey(PedestrianRegion.Tile.at(target)));
        var outside = java.util.stream.IntStream.range(0, 16).boxed().flatMap(x -> java.util.stream.IntStream.range(0, 16)
                .mapToObj(z -> new PedestrianGeometryRevision.Region(x, z)))
                .filter(r -> !proof.revisions().containsKey(new PedestrianRegion.Tile(r.x(), r.z()))).findFirst().orElseThrow();
        assertTrue(proof.matches(flat(revision.changed(Set.of(outside)), bounds, s -> false)));
        assertFalse(proof.matches(flat(revision.changed(Set.of(PedestrianGeometryRevision.Region.at(target.support()))), bounds, s -> false)));
    }

    @Test void requestHashingDoesNotRescanImmutableRegionalFactsAndCollisionsDoNotGrantEquality() {
        var reads = new AtomicInteger(); var scope = new Object(); var baseline = new Object();
        class Token { @Override public int hashCode() { reads.incrementAndGet(); return 7; } }
        var a = new PedestrianGeometryRevision(scope, baseline, java.util.Map.of(new PedestrianGeometryRevision.Region(0, 0), new Token()));
        var b = new PedestrianGeometryRevision(scope, baseline, java.util.Map.of(new PedestrianGeometryRevision.Region(0, 0), new Token()));
        int built = reads.get();
        for (int i = 0; i < 100; i++) assertEquals(a.hashCode(), b.hashCode());
        assertEquals(built, reads.get()); assertNotEquals(a, b);
    }
    @Test void separateTaskScopesShareCompatibleGraphsAndLongCorridorsWithoutBorrowingChangedPassages() {
        var terrain = new Object(); var epoch = new Object();
        var original = new PedestrianGeometryRevision(new Object(), terrain, epoch, java.util.Map.of());
        var bounds = new WorldBounds(0, 0, 512, 16);
        var start = SurfaceAnchor.at(1, 63, 1); var end = SurfaceAnchor.at(480, 63, 1);
        try (var planner = new CooperativePedestrianPlanner()) {
            var initial = finish(planner, flat(original, bounds, s -> false), start, end);
            assertEquals(PedestrianRouteResult.Status.FOUND, initial.status());
            long before = planner.workUnits();
            var other = new PedestrianGeometryRevision(new Object(), terrain, epoch, java.util.Map.of());
            var reads = new AtomicInteger();
            var shared = geometry(other, bounds, (x, z) -> {
                reads.incrementAndGet(); return SurfaceAnchor.at(x, 63, z);
            }, s -> false);
            var neighbourStart = SurfaceAnchor.at(2, 63, 2); var neighbourEnd = SurfaceAnchor.at(481, 63, 2);
            var joined = finish(planner, shared, neighbourStart, neighbourEnd);
            assertEquals("SHARED_KNOWN_CORRIDOR", joined.reason());
            assertEquals(neighbourStart, joined.route().getFirst());
            assertEquals(neighbourEnd, joined.route().getLast());
            assertEquals(0, reads.get(), "compatible task scopes reuse region geometry, not world surveying");
            assertTrue(planner.workUnits() - before < initial.workUnits() / 4,
                    "a shared long corridor needs only dependency checks and local connections");

            var restricted = new PedestrianGeometryRevision(new Object(), terrain, epoch,
                    java.util.Map.of(new PedestrianGeometryRevision.Region(6, 0), new Object()));
            var blocked = flat(restricted, bounds, s -> s.x() == 100);
            assertEquals(PedestrianRouteResult.Status.NO_PATH, finish(planner, blocked, neighbourStart, neighbourEnd).status(),
                    "a corridor proven under another passage policy grants no passage through a newly denied region");
            var reopened = flat(new PedestrianGeometryRevision(new Object(), terrain, epoch, java.util.Map.of()), bounds, s -> false);
            assertEquals(PedestrianRouteResult.Status.FOUND, finish(planner, reopened, start, neighbourEnd).status());
        }
    }

    @Test void sharedRegionGraphsKeepProfilesAndWorldEpochsSeparate() {
        var terrain = new Object(); var baseline = new Object(); var bounds = new WorldBounds(0, 0, 16, 16);
        var a = new PedestrianGeometryRevision(new Object(), terrain, baseline, java.util.Map.of());
        var b = new PedestrianGeometryRevision(new Object(), terrain, baseline, java.util.Map.of());
        var baseA = flat(a, bounds, s -> false); var baseB = flat(b, bounds, s -> false);
        var villager = new ProfiledPedestrianGeometry(baseA, UnitNavigationProfile.VILLAGER, p -> false);
        var donkey = new ProfiledPedestrianGeometry(baseB, UnitNavigationProfile.DONKEY, p -> false);
        assertNotEquals(villager.regionalRevision().orElseThrow().geometryScope(), donkey.regionalRevision().orElseThrow().geometryScope());
        var sameProfile = new ProfiledPedestrianGeometry(baseB, UnitNavigationProfile.VILLAGER, p -> false);
        assertEquals(villager.regionalRevision().orElseThrow().geometryScope(), sameProfile.regionalRevision().orElseThrow().geometryScope());
        assertNotEquals(villager.regionalRevision().orElseThrow().scope(), sameProfile.regionalRevision().orElseThrow().scope());
        var foreign = new PedestrianGeometryRevision(a.scope(), new Object(), new Object(), java.util.Map.of());
        assertNotEquals(a.geometryScope(), foreign.geometryScope());
    }

    private static PedestrianRouteGeometry geometry(PedestrianGeometryRevision revision, WorldBounds bounds,
            BiFunction<Integer, Integer, SurfaceAnchor> supports, Predicate<SurfaceAnchor> blocked) {
        return new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return bounds; }
            public SurfaceAnchor supportAt(int x, int z) { return supports.apply(x, z); }
            public boolean blocked(SurfaceAnchor surface) { return blocked.test(surface); }
        };
    }
    private static PedestrianRouteGeometry flat(PedestrianGeometryRevision revision, WorldBounds bounds, Predicate<SurfaceAnchor> blocked) {
        return geometry(revision, bounds, (x, z) -> SurfaceAnchor.at(x, 63, z), blocked);
    }
    private static PedestrianRouteResult finish(CooperativePedestrianPlanner planner, PedestrianRouteGeometry geometry,
                                               SurfaceAnchor start, SurfaceAnchor end) {
        PedestrianRouteResult result = planner.query(geometry, start, end);
        for (int turn = 0; turn < 4_096 && result.status() == PedestrianRouteResult.Status.PLANNING; turn++) {
            planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2); result = planner.query(geometry, start, end);
        }
        assertNotEquals(PedestrianRouteResult.Status.PLANNING, result.status()); return result;
    }

    @Test void remoteChangesReuseFoundResultAndComponentsWithoutAnotherSurveyButChangedRouteDoesNot() {
        var revision = PedestrianGeometryRevision.initial(); var bounds = new WorldBounds(0, 0, 256, 16);
        // Exercise actual regional construction; a short positive local proof
        // intentionally does not build a region for the next query to reuse.
        var start = SurfaceAnchor.at(1, 63, 1); var end = SurfaceAnchor.at(80, 63, 1);
        var old = flat(revision, bounds, surface -> false);
        try (var planner = new CooperativePedestrianPlanner()) {
            var found = finish(planner, old, start, end); long work = planner.workUnits(); planner.drainChanges();
            var remote = revision.changed(Set.of(new PedestrianGeometryRevision.Region(12, 0)));
            var reads = new AtomicInteger();
            var current = geometry(remote, bounds, (x, z) -> { reads.incrementAndGet(); return SurfaceAnchor.at(x, 63, z); }, s -> s.x() == 192);
            assertEquals(found, planner.peek(current, start, end).orElseThrow());
            assertEquals(found, planner.query(current, start, end));
            assertEquals(work, planner.workUnits()); assertEquals(0, reads.get());
            var changes = planner.drainChanges();
            assertTrue(changes.contains(new PedestrianPlanningChange(PedestrianRouteRequest.of(old, start, end), PedestrianPlanningChange.Kind.INVALIDATED)));
            assertTrue(changes.contains(new PedestrianPlanningChange(PedestrianRouteRequest.of(current, start, end), PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
            var adjacent = SurfaceAnchor.at(81, 63, 1);
            assertEquals(PedestrianRouteResult.Status.FOUND, finish(planner, current, start, adjacent).status());
            assertEquals(0, reads.get(), "an unchanged region component is shared across different local routes");
            assertTrue(planner.workUnits() - work < PedestrianRegion.BUILD_WORK,
                    "using an existing region cannot consume the cost of building it again");
            var broken = flat(remote.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0))), bounds, s -> s.x() == 8);
            assertTrue(planner.peek(broken, start, end).isEmpty());
            assertEquals(PedestrianRouteResult.Status.NO_PATH, finish(planner, broken, start, end).status());
        }
    }

    @Test void unfinishedSearchUsesNewGeometryWhenItExpandsIntoAPreviouslyUnvisitedChangedRegion() {
        var revision = PedestrianGeometryRevision.initial(); var bounds = new WorldBounds(0, 0, 512, 16);
        var start = SurfaceAnchor.at(1, 63, 1); var end = SurfaceAnchor.at(480, 63, 1);
        var frozen = new java.util.concurrent.atomic.AtomicBoolean();
        var old = geometry(revision, bounds, (x, z) -> { assertFalse(frozen.get(), "search cannot survey its obsolete geometry after rebase");
            return SurfaceAnchor.at(x, 63, z); }, s -> false);
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(old, start, end); planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            long work = planner.workUnits(); frozen.set(true);
            var current = flat(revision.changed(Set.of(new PedestrianGeometryRevision.Region(12, 0))), bounds, s -> s.x() == 192);
            assertEquals(PedestrianRouteResult.Status.PLANNING, planner.query(current, start, end).status());
            assertEquals(work, planner.workUnits(), "rebase compares knowledge tokens, not another search");
            assertEquals(1, planner.pendingCount());
            assertEquals(PedestrianRouteResult.Status.NO_PATH, finish(planner, current, start, end).status());
        }
    }

    @Test void changesInSurveyedRegionsCancelPendingWorkAndNegativeKnowledgeInsteadOfReusingEmptyRoutes() {
        var revision = PedestrianGeometryRevision.initial(); var bounds = new WorldBounds(0, 0, 64, 16);
        var start = SurfaceAnchor.at(1, 63, 1); var end = SurfaceAnchor.at(60, 63, 1);
        for (boolean unknown : List.of(false, true)) try (var planner = new CooperativePedestrianPlanner()) {
            var old = geometry(revision, bounds, (x, z) -> unknown && x == 8 ? null : SurfaceAnchor.at(x, 63, z), s -> !unknown && s.x() == 8);
            assertEquals(unknown ? PedestrianRouteResult.Status.UNKNOWN_GEOMETRY : PedestrianRouteResult.Status.NO_PATH,
                    finish(planner, old, start, end).status());
            var opened = flat(revision.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0))), bounds, s -> false);
            assertTrue(planner.peek(opened, start, end).isEmpty());
            assertEquals(PedestrianRouteResult.Status.FOUND, finish(planner, opened, start, end).status());
        }
        try (var planner = new CooperativePedestrianPlanner()) {
            var old = flat(revision, bounds, s -> false); planner.query(old, start, end);
            planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK);
            var blocked = flat(revision.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0))), bounds, s -> s.x() == 8);
            assertEquals(PedestrianRouteResult.Status.PLANNING, planner.query(blocked, start, end).status());
            assertEquals(1, planner.pendingCount());
            assertEquals(PedestrianRouteResult.Status.NO_PATH, finish(planner, blocked, start, end).status());
        }
    }

    @Test void scopeBoundsAndEvictedLineagesCannotBorrowAnotherViewsPermissionOrKnowledge() {
        var revision = PedestrianGeometryRevision.initial(); var bounds = new WorldBounds(0, 0, 32, 16);
        var start = SurfaceAnchor.at(1, 63, 1); var end = SurfaceAnchor.at(10, 63, 1);
        try (var planner = new CooperativePedestrianPlanner()) {
            finish(planner, flat(revision, bounds, s -> false), start, end);
            var foreign = flat(PedestrianGeometryRevision.initial(), bounds, s -> s.x() == 8);
            assertTrue(planner.peek(foreign, start, end).isEmpty());
            assertEquals(PedestrianRouteResult.Status.NO_PATH, finish(planner, foreign, start, end).status());
            var narrower = flat(revision, new WorldBounds(0, 0, 8, 16), s -> false);
            assertTrue(planner.peek(narrower, start, end).isEmpty());
            assertEquals(PedestrianRouteResult.Status.NO_PATH, finish(planner, narrower, start, end).status());
            var evicted = flat(PedestrianGeometryRevision.initial(revision.scope()), bounds, s -> s.x() == 8);
            assertTrue(planner.peek(evicted, start, end).isEmpty());
            assertEquals(PedestrianRouteResult.Status.NO_PATH, finish(planner, evicted, start, end).status());
        }
    }

    @Test void rebasedCapacityWaitRetainsItsExactAddressAndEventuallyReceivesAResult() {
        var revision = PedestrianGeometryRevision.initial(); var bounds = new WorldBounds(0, 0, 256, 16);
        var old = flat(revision, bounds, s -> false); var start = SurfaceAnchor.at(1, 63, 1); var end = SurfaceAnchor.at(12, 63, 1);
        try (var planner = new CooperativePedestrianPlanner()) {
            for (int i = 0; i < 8; i++) planner.query(old, start, SurfaceAnchor.at(3 + i, 63, 1));
            assertEquals("PLANNING_QUEUE_CAPACITY", planner.query(old, start, end).reason());
            var current = flat(revision.changed(Set.of(new PedestrianGeometryRevision.Region(12, 0))), bounds, s -> s.x() == 192);
            assertEquals("PLANNING_QUEUE_CAPACITY", planner.query(current, start, end).reason());
            assertTrue(planner.drainChanges().contains(new PedestrianPlanningChange(PedestrianRouteRequest.of(old, start, end), PedestrianPlanningChange.Kind.INVALIDATED)));
            assertEquals(PedestrianRouteResult.Status.FOUND, finish(planner, current, start, end).status());
        }
    }
    @Test void sameRegionRevisionCannotReuseAPortalOrDetourOutsideTheCurrentWorldBounds() {
        var revision = PedestrianGeometryRevision.initial(); var start = SurfaceAnchor.at(1, 63, 1); var end = SurfaceAnchor.at(10, 63, 1);
        var wide = flat(revision, new WorldBounds(0, 0, 32, 16), s -> s.x() == 8 && s.z() != 14);
        var narrow = flat(revision, new WorldBounds(0, 0, 32, 8), s -> s.x() == 8 && s.z() != 14);
        try (var planner = new CooperativePedestrianPlanner()) {
            assertEquals(PedestrianRouteResult.Status.FOUND, finish(planner, wide, start, end).status());
            assertEquals(PedestrianRouteResult.Status.NO_PATH, finish(planner, narrow, start, end).status());
        }
    }
}
