package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObstacleView;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianBoundaryCacheTest {
    private static final PedestrianRegion.Tile LEFT = new PedestrianRegion.Tile(0, 0), RIGHT = new PedestrianRegion.Tile(1, 0);
    private static PedestrianRouteGeometry geometry(PedestrianGeometryRevision revision, boolean plane, boolean blocked) {
        return new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(-32, -32, 128, 80); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return blocked && surface.x() == 16; }
            public OptionalInt uniformSupport(WorldBounds area) { return plane && !blocked ? OptionalInt.of(63) : OptionalInt.empty(); }
        };
    }
    private static PedestrianRegion region(PedestrianRegionCache cache, PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile) {
        return Objects.requireNonNull(cache.region(geometry, tile, 100_000).region());
    }
    private static List<PedestrianItineraryCache.Portal> links(PedestrianRegionCache cache, PedestrianRouteGeometry geometry,
            PedestrianRegion from, PedestrianRegion to) {
        List<PedestrianItineraryCache.Portal> result = null;
        for (int i = 0; i < 4096 && result == null; i++) {
            var lookup = cache.boundary(geometry, from, to, 32, () -> true);
            assertTrue(lookup.work() <= 32); result = lookup.portals();
        }
        return Objects.requireNonNull(result, "bounded boundary must finish");
    }
    @Test void layeredConnectionsAreSharedUntilAnAddressedChangeAndClearReleasesThem() {
        var revision = PedestrianGeometryRevision.initial(); var geometry = geometry(revision, false, false);
        var cache = new PedestrianRegionCache(); var from = region(cache, geometry, LEFT); var to = region(cache, geometry, RIGHT);
        assertNull(cache.boundary(geometry, from, to, 1, () -> true).portals(), "a partial producer cannot publish connectivity");
        var connected = links(cache, geometry, from, to); assertEquals(16, connected.size());
        assertTrue(connected.stream().allMatch(portal -> geometry.canTraverse(portal.exit(), portal.entrance())));
        assertEquals(16, cache.diagnostic().boundaries().traversalChecks());
        var remote = geometry(revision.changed(Set.of(new PedestrianGeometryRevision.Region(5, 0))), false, false);
        assertSame(connected, cache.boundary(remote, from, to, 1, () -> true).portals());
        assertEquals(1, cache.diagnostic().boundaries().buildsStarted());
        var changed = geometry(revision.changed(Set.of(new PedestrianGeometryRevision.Region(1, 0))), false, true);
        var blocked = links(cache, changed, region(cache, changed, LEFT), region(cache, changed, RIGHT));
        assertTrue(blocked.isEmpty(), "changed destination geometry cannot reuse the old positive boundary");
        assertSame(connected, links(cache, geometry, from, to), "alternative immutable branches remain independent");
        cache.clear(); assertEquals(0, cache.diagnostic().boundaries().retainedEntries());
        assertNotSame(connected, links(cache, geometry, from, to));
    }
    @Test void layeredIncomingConnectionsRemainDirectedAndRetainBothFloors() {
        var revision = PedestrianGeometryRevision.initial();
        PedestrianRouteGeometry geometry = new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 32, 16); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, x < 16 ? 64 : 63, z); }
            public List<SurfaceAnchor> surfacesAt(int x, int z) { return List.of(SurfaceAnchor.at(x, 60, z), supportAt(x, z)); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
            public boolean canTraverse(SurfaceAnchor from, SurfaceAnchor to) {
                return from.y() >= to.y() && PedestrianRouteGeometry.super.canTraverse(from, to);
            }
        };
        var cache = new PedestrianRegionCache(); var left = region(cache, geometry, LEFT); var right = region(cache, geometry, RIGHT);
        var outward = links(cache, geometry, left, right); var incoming = links(cache, geometry, right, left);
        assertEquals(32, outward.size()); assertEquals(16, incoming.size());
        assertTrue(outward.stream().anyMatch(portal -> portal.exit().y() == 64 && portal.entrance().y() == 63));
        assertTrue(incoming.stream().allMatch(portal -> portal.exit().y() == 60 && portal.entrance().y() == 60));
        assertSame(outward, cache.boundary(geometry, left, right, 1, () -> true).portals());
    }
    @Test void bodyProfilesCannotShareANarrowPassageProof() {
        var revision = PedestrianGeometryRevision.initial(); var base = geometry(revision, false, false);
        var walls = new HashSet<BlockPosition>();
        for (int x = 14; x <= 17; x++) for (int z : List.of(7, 9)) for (int y = 64; y <= 66; y++) walls.add(new BlockPosition(x, y, z));
        var obstacles = NavigationObstacleView.indexed(walls);
        var small = new ProfiledPedestrianGeometry(base, UnitNavigationProfile.VILLAGER, obstacles);
        var wide = new ProfiledPedestrianGeometry(base, UnitNavigationProfile.DONKEY, obstacles);
        var cache = new PedestrianRegionCache();
        var villagers = links(cache, small, region(cache, small, LEFT), region(cache, small, RIGHT));
        var donkeys = links(cache, wide, region(cache, wide, LEFT), region(cache, wide, RIGHT));
        assertTrue(villagers.stream().anyMatch(portal -> portal.exit().z() == 8));
        assertTrue(donkeys.stream().noneMatch(portal -> portal.exit().z() == 8));
        assertEquals(2, cache.diagnostic().boundaries().buildsCompleted());
    }
    @Test void concurrentReadersShareOneCooperativeBuilder() throws Exception {
        var revision = PedestrianGeometryRevision.initial(); var stop = new AtomicBoolean();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        PedestrianRouteGeometry geometry = new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 32, 16); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
            public boolean canTraverse(SurfaceAnchor from, SurfaceAnchor to) {
                if (stop.get()) {
                    entered.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("controlled boundary was not released"); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                }
                return PedestrianRouteGeometry.super.canTraverse(from, to);
            }
        };
        var cache = new PedestrianRegionCache(); var from = region(cache, geometry, LEFT); var to = region(cache, geometry, RIGHT);
        try (var executor = Executors.newSingleThreadExecutor()) {
            stop.set(true); var first = executor.submit(() -> cache.boundary(geometry, from, to, 1024, () -> true));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertNull(cache.boundary(geometry, from, to, 1, () -> true).portals());
                assertEquals(1, cache.diagnostic().boundaries().buildsStarted());
                cache.clear();
            } finally { stop.set(false); release.countDown(); }
            var completed = first.get(5, TimeUnit.SECONDS).portals(); assertNotNull(completed);
            assertEquals(0, cache.diagnostic().boundaries().retainedEntries(), "a detached builder cannot republish into the cleared cache");
            assertNull(cache.boundary(geometry, from, to, 1, () -> true).portals());
        }
    }
    @Test void analyticalTrafficNeedsNoBodyChecksAndCannotEvictLayeredPassages() {
        var revision = PedestrianGeometryRevision.initial(); var checks = new AtomicInteger();
        PedestrianRouteGeometry geometry = new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 40_000, 32); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
            public OptionalInt uniformSupport(WorldBounds area) { return area.minX() >= 32 ? OptionalInt.of(63) : OptionalInt.empty(); }
            public boolean canTraverse(SurfaceAnchor from, SurfaceAnchor to) {
                checks.incrementAndGet(); return PedestrianRouteGeometry.super.canTraverse(from, to);
            }
        };
        var cache = new PedestrianRegionCache(); var from = region(cache, geometry, LEFT); var to = region(cache, geometry, RIGHT);
        var layered = links(cache, geometry, from, to); int before = checks.get();
        for (int x = 2; x <= 2200; x++) {
            var a = region(cache, geometry, new PedestrianRegion.Tile(x, 0));
            var b = region(cache, geometry, new PedestrianRegion.Tile(x + 1, 0));
            var plane = cache.boundary(geometry, a, b, 1, () -> true);
            assertEquals(16, Objects.requireNonNull(plane.portals()).size());
            assertEquals(1, plane.work());
        }
        assertEquals(before, checks.get(), "a certified union needs no per-cell body traversal checks");
        assertEquals(1, cache.diagnostic().boundaries().retainedEntries());
        assertSame(layered, cache.boundary(geometry, from, to, 1, () -> true).portals());
    }
    @Test void retiredScopesCannotKeepTheirBoundaryPublicationsAliveInTheCache() {
        var cache = new PedestrianRegionCache(); PedestrianRouteGeometry first = null;
        List<PedestrianItineraryCache.Portal> previous = null;
        for (int i = 0; i < 65; i++) {
            var geometry = geometry(PedestrianGeometryRevision.initial(), false, false);
            var value = links(cache, geometry, region(cache, geometry, LEFT), region(cache, geometry, RIGHT));
            if (i == 0) { first = geometry; previous = value; }
        }
        assertEquals(64, cache.diagnostic().boundaries().retainedEntries());
        var current = links(cache, first, region(cache, first, LEFT), region(cache, first, RIGHT));
        assertNotSame(previous, current, "a retired geometry scope must obtain a new publication");
        cache.clear(); assertEquals(0, cache.diagnostic().boundaries().retainedPortals());
    }
}
