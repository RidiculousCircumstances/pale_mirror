package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class UnitNavigationProfileTest {
    @Test void repeatedKnownAndUnknownClearanceQueriesStayLocalToTheirImmutableObservation() {
        for (boolean known : new boolean[]{false, true}) {
            var reads = new java.util.concurrent.atomic.AtomicInteger();
            PedestrianRouteGeometry raw = new PedestrianRouteGeometry() {
                public WorldBounds bounds() { return new WorldBounds(-32, -32, 64, 64); }
                public SurfaceAnchor supportAt(int x, int z) { return floor(x, z); }
                public boolean blocked(SurfaceAnchor surface) { return false; }
                public boolean blockKnown(BlockPosition position) { reads.incrementAndGet(); return known; }
            };
            var geometry = new ProfiledPedestrianGeometry(raw, UnitNavigationProfile.DONKEY, ignored -> false);
            assertEquals(known, geometry.clearanceKnown(floor(0, 0))); int first = reads.get();
            assertTrue(first > 0);
            for (int repeat = 0; repeat < 20; repeat++) {
                assertEquals(known, geometry.clearanceKnown(floor(0, 0)));
                assertEquals(known, geometry.clearBody(floor(0, 0)));
            }
            assertEquals(first, reads.get(), "edge expansion must not reread the same captured footprint");
        }
    }
    @Test void cachedWideNeighbourFactsAndAbsenceRemainExactAcrossEvictionDamageAndMaterialization() {
        var raw = PedestrianGeometryRevision.initial().changed(Set.of(new PedestrianGeometryRevision.Region(0, 0),
                new PedestrianGeometryRevision.Region(1, 0)));
        var wide = raw.profiled(UnitNavigationProfile.DONKEY);
        var original = wide.at(0, 0); var absent = wide.at(-20, -20);
        for (int i = 0; i < 300; i++) assertEquals(absent, wide.at(100 + i, -100));
        assertEquals(original, wide.at(0, 0), "eviction only recomputes the same complete neighbour tuple");
        var changed = raw.changed(Set.of(new PedestrianGeometryRevision.Region(-19, -19))).profiled(UnitNavigationProfile.DONKEY);
        assertNotEquals(absent, changed.at(-20, -20), "new knowledge in the footprint cannot inherit cached absence");
        assertEquals(absent, wide.at(-20, -20), "the predecessor stays immutable");
        assertEquals(original, wide.revisions().get(new PedestrianGeometryRevision.Region(0, 0)), "full hash/ingress and lazy reads describe the same facts");
        assertEquals(original, wide.at(0, 0));
    }
    @Test void rebuildingWideFootprintTokensCannotDiscardAnUnchangedAcceptedRouteButRealLocalDamageDoes() {
        var source = PedestrianGeometryRevision.initial().changed(Set.of(new PedestrianGeometryRevision.Region(0, 0)))
                .changed(Set.of(new PedestrianGeometryRevision.Region(1, 0)));
        var equalProfile = new UnitNavigationProfile(UnitNavigationProfile.DONKEY.widthMicros(),
                UnitNavigationProfile.DONKEY.heightMicros(), 1, 1, UnitNavigationProfile.DONKEY.capabilities());
        assertSame(source.profiled(UnitNavigationProfile.DONKEY), source.profiled(equalProfile),
                "equal declared body envelopes reuse the complete immutable revision");
        var distant = source.changed(Set.of(new PedestrianGeometryRevision.Region(20, 20)));
        var original = new ProfiledPedestrianGeometry(terrain(Set.of(), source, UnitNavigationProfileTest::floor),
                UnitNavigationProfile.DONKEY, ignored -> false);
        var rebuilt = new ProfiledPedestrianGeometry(terrain(Set.of(), distant, UnitNavigationProfileTest::floor),
                UnitNavigationProfile.DONKEY, ignored -> false);
        assertFalse(rebuilt.regionalRevision().orElseThrow().changesSince(original.regionalRevision().orElseThrow())
                .contains(new PedestrianGeometryRevision.Region(0, 0)));
        var start = floor(0, 0); var target = floor(2, 0);
        try (var planner = new CooperativePedestrianPlanner()) {
            planner.query(original, start, target);
            while (planner.pendingCount() > 0) planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            long work = planner.workUnits();
            assertEquals(PedestrianRouteResult.Status.FOUND, planner.query(rebuilt, start, target).status());
            assertEquals(work, planner.workUnits(), "unchanged composite evidence must not start the same search again");
            var wall = Set.of(new BlockPosition(2, 65, 0));
            var changed = new ProfiledPedestrianGeometry(terrain(wall,
                    distant.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0))), UnitNavigationProfileTest::floor),
                    UnitNavigationProfile.DONKEY, wall::contains);
            assertNotEquals(PedestrianRouteResult.Status.FOUND, planner.query(changed, start, target).status());
        }
    }
    @Test void repeatedSweptBodyQueriesReuseOnlyTheirCapturedGeometryAndChangedKnowledgeClosesTheEdge() {
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var raw = terrain(Set.of(), PedestrianGeometryRevision.initial(), (x, z) -> {
            reads.incrementAndGet(); return floor(x, z);
        });
        var geometry = new ProfiledPedestrianGeometry(raw, UnitNavigationProfile.DONKEY, ignored -> false);
        var from = floor(0, 0); var to = floor(1, 0);
        assertTrue(geometry.canTraverse(from, to)); int first = reads.get();
        for (int repeat = 0; repeat < 20; repeat++) assertTrue(geometry.canTraverse(from, to));
        assertEquals(first, reads.get(), "identical profile volumes must not survey terrain again per graph edge");
        var blocked = Set.of(new BlockPosition(1, 65, 0));
        var changed = raw.regionalRevision().orElseThrow().changed(Set.of(new PedestrianGeometryRevision.Region(0, 0)));
        var next = new ProfiledPedestrianGeometry(terrain(blocked, changed, UnitNavigationProfileTest::floor),
                UnitNavigationProfile.DONKEY, blocked::contains);
        assertFalse(next.canTraverse(from, to), "an old clear volume cannot authorize a changed geometry view");
        assertTrue(geometry.canTraverse(from, to), "retained immutable evidence is not mutated in place");
    }
    private static SurfaceAnchor floor(int x, int z) { return SurfaceAnchor.at(x, 64, z); }
    private static PedestrianRouteGeometry terrain(Set<BlockPosition> obstacles, PedestrianGeometryRevision revision,
                                                  java.util.function.BiFunction<Integer, Integer, SurfaceAnchor> supports) {
        return new PedestrianRouteGeometry() {
            @Override public Object version() { return revision; }
            @Override public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            @Override public WorldBounds bounds() { return new WorldBounds(-32, -32, 64, 64); }
            @Override public SurfaceAnchor supportAt(int x, int z) { return supports.apply(x, z); }
            @Override public boolean supportAvailable(SurfaceAnchor surface) { return !obstacles.contains(surface.support()); }
            @Override public boolean blocked(SurfaceAnchor surface) {
                return !supportAvailable(surface) || obstacles.contains(surface.support().offset(0, 1, 0))
                        || obstacles.contains(surface.support().offset(0, 2, 0));
            }
        };
    }
    @Test void oneBlockDoorIsPassableForResidentButNotPackBodyAndCannotShareItsRouteKey() {
        var walls = new java.util.HashSet<BlockPosition>();
        for (int x : new int[]{-1, 1}) for (int z = -3; z <= 3; z++) for (int y = 65; y <= 66; y++)
            walls.add(new BlockPosition(x, y, z));
        var raw = terrain(walls, PedestrianGeometryRevision.initial(), UnitNavigationProfileTest::floor);
        var resident = new ProfiledPedestrianGeometry(raw, UnitNavigationProfile.VILLAGER, walls::contains);
        var animal = new ProfiledPedestrianGeometry(raw, UnitNavigationProfile.DONKEY, walls::contains);
        assertFalse(resident.blocked(floor(0, 0))); assertTrue(animal.blocked(floor(0, 0)));
        assertNotEquals(resident.version(), animal.version());
        assertEquals(PedestrianRouteResult.Status.FOUND, PedestrianRoutePlanning.calculate(resident, floor(0, -2), floor(0, 2)).status());
        assertNotEquals(PedestrianRouteResult.Status.FOUND, PedestrianRoutePlanning.calculate(animal, floor(0, -2), floor(0, 2)).status());
    }
    @Test void capturedEdgeUsesOnlyItsLocalRegionalFactsAndNeverReusesAChangedNeighbour() {
        var revision = new PedestrianGeometryRevision(new Object(), new Object(), new String("baseline"), java.util.Map.of());
        var from = floor(0, 0); var to = floor(1, 0);
        var original = new ProfiledPedestrianGeometry(terrain(Set.of(), revision, UnitNavigationProfileTest::floor),
                UnitNavigationProfile.DONKEY, ignored -> false);
        assertTrue(original.canTraverse(from, to));
        var distant = revision.changed(Set.of(new PedestrianGeometryRevision.Region(1000, 1000)));
        var reconstructed = new ProfiledPedestrianGeometry(terrain(Set.of(), distant, (x, z) -> {
            throw new AssertionError("unchanged local edge re-read terrain after a remote change");
        }), UnitNavigationProfile.DONKEY, ignored -> false);
        assertTrue(reconstructed.canTraverse(from, to));
        var otherQuery = new PedestrianGeometryRevision(new Object(), revision.geometryScope(), revision.baseline(), java.util.Map.of());
        var sameDeclaredLocalGeometry = new ProfiledPedestrianGeometry(terrain(Set.of(), otherQuery, (x, z) -> {
            throw new AssertionError("explicitly shared local geometry was recomputed for another query");
        }), UnitNavigationProfile.DONKEY, ignored -> false);
        assertTrue(sameDeclaredLocalGeometry.canTraverse(from, to));
        var wall = Set.of(new BlockPosition(1, 65, 0), new BlockPosition(1, 66, 0));
        var changed = revision.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0)));
        var obstructed = new ProfiledPedestrianGeometry(terrain(wall, changed, UnitNavigationProfileTest::floor),
                UnitNavigationProfile.DONKEY, wall::contains);
        assertFalse(obstructed.canTraverse(from, to));
        assertTrue(original.canTraverse(from, to));
        var foreignEpoch = new ProfiledPedestrianGeometry(terrain(wall, PedestrianGeometryRevision.initial(),
                UnitNavigationProfileTest::floor), UnitNavigationProfile.DONKEY, wall::contains);
        assertFalse(foreignEpoch.canTraverse(from, to));
        var otherBaseline = new PedestrianGeometryRevision(new Object(), revision.geometryScope(), new String("baseline"), java.util.Map.of());
        var resetBaseline = new ProfiledPedestrianGeometry(terrain(wall, otherBaseline, UnitNavigationProfileTest::floor),
                UnitNavigationProfile.DONKEY, wall::contains);
        assertFalse(resetBaseline.canTraverse(from, to), "equal labels cannot replace the exact baseline epoch");
        var cleared = new ProfiledPedestrianGeometry(terrain(Set.of(), changed.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0))),
                UnitNavigationProfileTest::floor), UnitNavigationProfile.DONKEY, ignored -> false);
        assertTrue(cleared.canTraverse(from, to), "an old negative result cannot survive local clearance");
        var boundary = PedestrianGeometryRevision.initial(); var leftWall = new java.util.HashSet<BlockPosition>();
        for (int z = -2; z <= 3; z++) for (int y = 65; y <= 66; y++) leftWall.add(new BlockPosition(14, y, z));
        var openBoundary = new ProfiledPedestrianGeometry(terrain(leftWall, boundary, UnitNavigationProfileTest::floor),
                UnitNavigationProfile.DONKEY, leftWall::contains);
        assertTrue(openBoundary.canTraverse(floor(15, 0), floor(15, 1)));
        var bothWalls = new java.util.HashSet<>(leftWall);
        for (int z = -2; z <= 3; z++) for (int y = 65; y <= 66; y++) bothWalls.add(new BlockPosition(16, y, z));
        var changedNeighbour = new ProfiledPedestrianGeometry(terrain(bothWalls,
                boundary.changed(Set.of(new PedestrianGeometryRevision.Region(1, 0))), UnitNavigationProfileTest::floor),
                UnitNavigationProfile.DONKEY, bothWalls::contains);
        assertFalse(changedNeighbour.canTraverse(floor(15, 0), floor(15, 1)), "wide-body facts include the neighbouring region");
    }

    @Test void wideBodyCanApproachAndStepOntoRoadWithoutMakingAdjacentLowColumnACentredStation() {
        var raw = terrain(Set.of(), PedestrianGeometryRevision.initial(), (x, z) -> SurfaceAnchor.at(x, x >= 1 ? 65 : 64, z));
        var animal = new ProfiledPedestrianGeometry(raw, UnitNavigationProfile.DONKEY, ignored -> false);
        assertFalse(animal.blocked(floor(0, 0)), "there is a clear offset body pose before the rise");
        assertFalse(animal.clearStation(floor(0, 0)), "the centred body would intersect the rise");
        assertTrue(animal.canTraverse(floor(0, 0), SurfaceAnchor.at(1, 65, 0)));
        var noStep = new UnitNavigationProfile(1_396_485, 1_500_000, 0, 1, Set.of(TraversalCapability.PEDESTRIAN));
        var dropOnly = new ProfiledPedestrianGeometry(raw, noStep, ignored -> false);
        for (int repeat = 0; repeat < 2; repeat++) {
            assertFalse(dropOnly.canTraverse(floor(0, 0), SurfaceAnchor.at(1, 65, 0)));
            assertTrue(dropOnly.canTraverse(SurfaceAnchor.at(1, 65, 0), floor(0, 0)), "the reverse edge has its own drop policy");
        }
        var wall = Set.of(new BlockPosition(1, 66, 0), new BlockPosition(1, 67, 0));
        var changed = new ProfiledPedestrianGeometry(terrain(wall, PedestrianGeometryRevision.initial(),
                (x, z) -> SurfaceAnchor.at(x, x >= 1 ? 65 : 64, z)), UnitNavigationProfile.DONKEY, wall::contains);
        assertFalse(changed.canTraverse(floor(0, 0), SurfaceAnchor.at(1, 65, 0)), "a newly obstructed geometry cannot reuse an accepted edge");
        assertTrue(animal.canTraverse(floor(0, 0), SurfaceAnchor.at(1, 65, 0)), "the predecessor remains immutable");
    }
    @Test void heightUnknownFootprintAndNeighbourRegionChangesAreNotSilentlyIgnored() {
        var roof = Set.of(new BlockPosition(0, 66, 0));
        var initial = PedestrianGeometryRevision.initial();
        var raw = terrain(roof, initial, UnitNavigationProfileTest::floor);
        var shortBody = new UnitNavigationProfile(600_000, 900_000, 1, 1, Set.of(TraversalCapability.PEDESTRIAN));
        assertFalse(new ProfiledPedestrianGeometry(raw, shortBody, roof::contains).blocked(floor(0, 0)));
        assertTrue(new ProfiledPedestrianGeometry(raw, UnitNavigationProfile.VILLAGER, roof::contains).blocked(floor(0, 0)));
        var unknown = terrain(Set.of(), initial, (x, z) -> x == 0 ? floor(x, z) : null);
        assertTrue(new ProfiledPedestrianGeometry(unknown, UnitNavigationProfile.DONKEY, ignored -> false).blocked(floor(0, 0)));
        var prior = new ProfiledPedestrianGeometry(raw, UnitNavigationProfile.DONKEY, roof::contains).regionalRevision().orElseThrow();
        var changed = initial.changed(Set.of(new PedestrianGeometryRevision.Region(1, 0)));
        var next = new ProfiledPedestrianGeometry(terrain(roof, changed, UnitNavigationProfileTest::floor),
                UnitNavigationProfile.DONKEY, roof::contains).regionalRevision().orElseThrow();
        assertTrue(next.changesSince(prior).contains(new PedestrianGeometryRevision.Region(0, 0)));
        assertFalse(next.changesSince(prior).contains(new PedestrianGeometryRevision.Region(-1, 0)));
    }
    @Test void canonicalPoseUpdatesPreserveExplicitEnvelopeAndGroupUsesIntersectionOfCapabilities() {
        var location = ActorLocation.standingOn(floor(0, 0), ActorKind.PACK_ANIMAL, UnitNavigationProfile.DONKEY);
        assertEquals(location.navigationProfile(), location.withBody(floor(3, 2).standingBody()).navigationProfile());
        assertEquals(location.navigationProfile(), location.deadAt(location.body()).navigationProfile());
        var group = UnitNavigationProfile.enclosing(java.util.List.of(UnitNavigationProfile.VILLAGER, UnitNavigationProfile.DONKEY));
        assertEquals(UnitNavigationProfile.DONKEY.widthMicros(), group.widthMicros());
        assertEquals(UnitNavigationProfile.VILLAGER.heightMicros(), group.heightMicros());
        assertEquals(Set.of(TraversalCapability.PEDESTRIAN), group.capabilities());
        assertThrows(NullPointerException.class, () -> ActorLocation.standingOn(floor(0, 0), ActorKind.RESIDENT, null));
    }
}
