package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class UnitNavigationProfileTest {
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
    @Test void wideBodyCanApproachAndStepOntoRoadWithoutMakingAdjacentLowColumnACentredStation() {
        var raw = terrain(Set.of(), PedestrianGeometryRevision.initial(), (x, z) -> SurfaceAnchor.at(x, x >= 1 ? 65 : 64, z));
        var animal = new ProfiledPedestrianGeometry(raw, UnitNavigationProfile.DONKEY, ignored -> false);
        assertFalse(animal.blocked(floor(0, 0)), "there is a clear offset body pose before the rise");
        assertFalse(animal.clearStation(floor(0, 0)), "the centred body would intersect the rise");
        assertTrue(animal.canTraverse(floor(0, 0), SurfaceAnchor.at(1, 65, 0)));
        var noStep = new UnitNavigationProfile(1_396_485, 1_500_000, 0, 1, Set.of(TraversalCapability.PEDESTRIAN));
        assertFalse(new ProfiledPedestrianGeometry(raw, noStep, ignored -> false).canTraverse(floor(0, 0), SurfaceAnchor.at(1, 65, 0)));
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
