package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObstacleView;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianCertifiedRegionTest {
    @Test void certifiedTrafficCannotDiscardLayeredGraphsOrUnfinishedSurveys() {
        var revision = PedestrianGeometryRevision.initial(); var firstColumn = new AtomicInteger();
        PedestrianRouteGeometry geometry = new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 40_000, 16); }
            public OptionalInt uniformSupport(WorldBounds area) { return area.minX() == 0 ? OptionalInt.empty() : OptionalInt.of(63); }
            public SurfaceAnchor supportAt(int x, int z) {
                if (x == 0 && z == 0) firstColumn.incrementAndGet();
                return SurfaceAnchor.at(x, 63, z);
            }
            public boolean blocked(SurfaceAnchor surface) { return false; }
        };
        var cache = new PedestrianRegionCache(); var first = new PedestrianRegion.Tile(0, 0);
        assertNull(cache.region(geometry, first, 1).region());
        for (int x = 1; x <= 2200; x++) assertNotNull(cache.region(geometry, new PedestrianRegion.Tile(x, 0), 1).region());
        assertEquals(0, cache.diagnostic().evictions(), "one-unit plane proofs do not displace expensive graphs");
        var completed = cache.region(geometry, first, 100_000).region(); assertNotNull(completed);
        assertEquals(1, firstColumn.get(), "unrelated ready graphs cannot restart the admitted survey");
        for (int x = 1; x <= 2200; x++) assertNotNull(cache.region(geometry, new PedestrianRegion.Tile(x, 0), 1).region());
        assertSame(completed, cache.region(geometry, first, 1).region(), "the completed layered graph also survives cheap traffic");
        var route = completed.refine(SurfaceAnchor.at(0, 63, 0), SurfaceAnchor.at(15, 63, 15));
        for (int turn = 0; turn < 100 && route.result() == null; turn++) route.advance(32);
        KnownPedestrianNavigation.requireRoute(geometry, Objects.requireNonNull(route.result()));
    }
    @Test void actualLayeredRetentionPressureProtectsUnfinishedSurvey() {
        var revision = PedestrianGeometryRevision.initial(); var firstColumn = new AtomicInteger();
        PedestrianRouteGeometry geometry = new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 128_000, 16); }
            public OptionalInt uniformSupport(WorldBounds area) { return area.minX() >= 3301 * 16 ? OptionalInt.of(63) : OptionalInt.empty(); }
            public SurfaceAnchor supportAt(int x, int z) {
                if (x == 0 && z == 0) firstColumn.incrementAndGet();
                return x < 16 || x >= 3301 * 16 || x % 16 == 0 && z == 0 ? SurfaceAnchor.at(x, 63, z) : null;
            }
            public boolean blocked(SurfaceAnchor surface) { return false; }
        };
        var cache = new PedestrianRegionCache(); var first = new PedestrianRegion.Tile(0, 0);
        assertNull(cache.region(geometry, first, 1).region());
        var heldBySearch = cache.region(geometry, new PedestrianRegion.Tile(1, 0), 100_000).region();
        assertNotNull(heldBySearch);
        for (int x = 2; x <= 3300; x++) assertNotNull(cache.region(geometry, new PedestrianRegion.Tile(x, 0), 100_000).region());
        assertTrue(cache.diagnostic().evictions() > 0, "real layered graphs still obey the unchanged retention bound");
        assertNotNull(cache.region(geometry, first, 100_000).region());
        assertEquals(0, cache.diagnostic().recentEvictedRebuilds(), "one-use geography is not a repeated-key rebuild");
        long builds = cache.diagnostic().buildsStarted();
        assertSame(heldBySearch, cache.region(geometry, new PedestrianRegion.Tile(1, 0), 1).region(),
                "another search must share an existing immutable graph after strong-cache eviction");
        assertEquals(builds, cache.diagnostic().buildsStarted(), "sharing cannot resurvey the still-live graph");
        assertEquals(0, cache.diagnostic().recentEvictedRebuilds());
        assertEquals(1, cache.diagnostic().weakGraphHits());
        assertTrue(cache.retentionDiagnostic().get("weakEntries") <= 8192);
        long hits = cache.diagnostic().completedHits();
        assertNotNull(cache.region(geometry, new PedestrianRegion.Tile(1, 0), 1).region());
        assertEquals(hits + 1, cache.diagnostic().completedHits());
        long evictions = cache.diagnostic().evictions();
        for (int x = 3301; x <= 5500; x++) assertNotNull(cache.region(geometry, new PedestrianRegion.Tile(x, 0), 1).region());
        assertEquals(evictions, cache.diagnostic().evictions(), "plane certificates cannot evict a graph even when the cache is full");
        assertEquals(1, firstColumn.get(), "completed graph pressure cannot erase unfinished work");
        var changedRevision = revision.changed(Set.of(new PedestrianGeometryRevision.Region(1, 0)));
        PedestrianRouteGeometry changed = new PedestrianRouteGeometry() {
            public Object version() { return changedRevision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(changedRevision); }
            public WorldBounds bounds() { return geometry.bounds(); }
            public SurfaceAnchor supportAt(int x, int z) { return geometry.supportAt(x, z); }
            public boolean blocked(SurfaceAnchor surface) { return true; }
        };
        var blocked = cache.region(changed, new PedestrianRegion.Tile(1, 0), 100_000).region();
        assertNotNull(blocked); assertEquals(0, blocked.size(), "a changed local revision cannot borrow the old weak graph");
        assertSame(heldBySearch, cache.region(geometry, new PedestrianRegion.Tile(1, 0), 1).region());
        cache.clear();
        assertEquals(0, cache.retentionDiagnostic().get("weakEntries"));
        assertNull(cache.region(geometry, new PedestrianRegion.Tile(1, 0), 1).region(),
                "clearing the cache must sever reuse even when a search still holds the old graph");
        java.lang.ref.Reference.reachabilityFence(heldBySearch);
    }
    @Test void reusedLayeredGraphSurvivesAOneUseSurveyWithoutPinningEveryNewGraph() {
        var revision = PedestrianGeometryRevision.initial();
        PedestrianRouteGeometry geometry = new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 128_000, 16); }
            public SurfaceAnchor supportAt(int x, int z) { return x % 16 == 0 && z == 0 ? SurfaceAnchor.at(x, 63, z) : null; }
            public boolean blocked(SurfaceAnchor surface) { return false; }
        };
        var cache = new PedestrianRegionCache(); var local = new PedestrianRegion.Tile(0, 0);
        var graph = cache.region(geometry, local, 100_000).region(); assertNotNull(graph);
        assertSame(graph, cache.region(geometry, local, 1).region(), "a second calculation demonstrates actual reuse");
        for (int x = 1; x <= 2200; x++) assertNotNull(cache.region(geometry, new PedestrianRegion.Tile(x, 0), 100_000).region());
        assertTrue(cache.diagnostic().evictions() > 0);
        assertSame(graph, cache.region(geometry, local, 1).region(),
                "one-use distant surveys must not evict a proven reusable local graph");
        assertTrue(cache.diagnostic().retainedEntries() <= 2048);
        var changed = new PedestrianGeometryRevision(revision.scope(), revision.geometryScope(), revision.baseline(),
                Map.of(new PedestrianGeometryRevision.Region(0, 0), new Object()));
        PedestrianRouteGeometry blocked = new PedestrianRouteGeometry() {
            public Object version() { return changed; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(changed); }
            public WorldBounds bounds() { return geometry.bounds(); }
            public SurfaceAnchor supportAt(int x, int z) { return geometry.supportAt(x, z); }
            public boolean blocked(SurfaceAnchor surface) { return true; }
        };
        var denied = cache.region(blocked, local, 100_000).region(); assertNotNull(denied);
        assertEquals(0, denied.size(), "retention grants no reuse under changed permissions");
        assertSame(graph, cache.region(geometry, local, 1).region());
        // Strong reuse is bounded. After eviction a graph still held by a
        // calculation remains reusable through the bounded weak directory.
        for (int x = 2201; x <= 3300; x++) {
            var tile = new PedestrianRegion.Tile(x, 0);
            var next = cache.region(geometry, tile, 100_000).region(); assertNotNull(next);
            assertSame(next, cache.region(geometry, tile, 1).region());
        }
        for (int x = 3301; x <= 5500; x++) assertNotNull(cache.region(geometry, new PedestrianRegion.Tile(x, 0), 100_000).region());
        long weakHits = cache.diagnostic().weakGraphHits();
        assertSame(graph, cache.region(geometry, local, 1).region(), "a still-live evicted graph needs no second survey");
        assertEquals(weakHits + 1, cache.diagnostic().weakGraphHits(), "the old graph left strong retention");
        var retention = cache.retentionDiagnostic();
        assertTrue(retention.get("entries") <= 2048); assertTrue(retention.get("reservedSurfaceSlots") <= 2048L * 256);
        cache.clear(); assertEquals(0, cache.retentionDiagnostic().get("reservedSurfaceSlots"));
    }
    @Test void retiredGeometryScopesReleaseEveryRetentionQueue() {
        for (boolean versioned : List.of(false, true)) {
            var cache = new PedestrianRegionCache(); var tile = new PedestrianRegion.Tile(0, 0);
            var views = new ArrayList<PedestrianRouteGeometry>();
            for (int i = 0; i < 65; i++) {
                var revision = PedestrianGeometryRevision.initial();
                var geometry = new PedestrianRouteGeometry() {
                    public Object version() { return revision; }
                    public Optional<PedestrianGeometryRevision> regionalRevision() { return versioned ? Optional.of(revision) : Optional.empty(); }
                    public WorldBounds bounds() { return new WorldBounds(0, 0, 16, 16); }
                    public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
                    public boolean blocked(SurfaceAnchor surface) { return false; }
                };
                views.add(geometry);
                var built = cache.region(geometry, tile, 100_000).region(); assertNotNull(built);
                assertSame(built, cache.region(geometry, tile, 1).region());
            }
            assertEquals(versioned ? 64 : 1, cache.diagnostic().retainedEntries());
            assertEquals(cache.diagnostic().retainedEntries() * 256,
                    cache.retentionDiagnostic().get("reservedSurfaceSlots"));
            assertNull(cache.region(views.getFirst(), tile, 1).region(), "retired scope cannot supply an old graph");
            assertNotNull(cache.region(views.getFirst(), tile, 100_000).region());
            cache.clear(); assertEquals(0, cache.retentionDiagnostic().get("entries"));
            assertEquals(0, cache.retentionDiagnostic().get("builders"));
        }
    }
    @Test void clearedWorkerCannotRepublishDetachedRetention() {
        var cache = new PedestrianRegionCache(); var tile = new PedestrianRegion.Tile(0, 0);
        var geometry = plane(false, new AtomicInteger()); var cleared = new java.util.concurrent.atomic.AtomicBoolean();
        var detached = cache.region(geometry, tile, 100_000, () -> {
            if (cleared.compareAndSet(false, true)) cache.clear();
            return true;
        }).region();
        assertNotNull(detached, "an already admitted worker can finish its immutable private result");
        assertEquals(0, cache.retentionDiagnostic().get("entries"));
        assertEquals(0, cache.retentionDiagnostic().get("reservedSurfaceSlots"));
        var current = cache.region(geometry, tile, 100_000).region(); assertNotNull(current); assertNotSame(detached, current);
        assertSame(current, cache.region(geometry, tile, 1).region());
    }
    private static PedestrianRouteGeometry plane(boolean certified, AtomicInteger reads) {
        return new PedestrianRouteGeometry() {
            public WorldBounds bounds() { return new WorldBounds(-32, -32, 128, 128); }
            public SurfaceAnchor supportAt(int x, int z) { reads.incrementAndGet(); return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
            public OptionalInt uniformSupport(WorldBounds area) { return certified ? OptionalInt.of(63) : OptionalInt.empty(); }
        };
    }
    private static PedestrianRegion region(PedestrianRouteGeometry geometry) {
        var build = new PedestrianRegionBuild(geometry, new PedestrianRegion.Tile(-1, -1));
        for (int turn = 0; turn < 100 && build.result() == null; turn++) build.advance(256);
        return Objects.requireNonNull(build.result());
    }
    @Test void certifiedPlaneHasTheSameDirectedGraphWithoutPerCellSurvey() {
        var reads = new AtomicInteger(); var fast = region(plane(true, reads));
        assertEquals(0, reads.get(), "only an explicit producer certificate bypasses the expensive block projection");
        var reference = region(plane(false, reads)); assertTrue(reads.get() > 0);
        assertEquals(reference.size(), fast.size()); assertFalse(fast.unknown());
        for (int index = 0; index < reference.size(); index++) {
            assertEquals(reference.surface(index), fast.surface(index));
            assertEquals(reference.component(reference.surface(index)), fast.component(fast.surface(index)));
            assertEquals(reference.outgoing(index), fast.outgoing(index));
        }
        assertEquals(-1, fast.component(SurfaceAnchor.at(-17, 63, -1)));
        assertEquals(-1, fast.component(SurfaceAnchor.at(-1, 62, -1)));
        for (var endpoints : List.of(List.of(0, 255), List.of(255, 0), List.of(120, 120))) {
            var from = fast.surface(endpoints.getFirst()); var to = fast.surface(endpoints.getLast());
            var refinement = fast.refine(from, to);
            for (int turn = 0; turn < 64 && refinement.result() == null; turn++) assertTrue(refinement.advance(1) <= 1);
            var route = Objects.requireNonNull(refinement.result());
            assertEquals(from, route.getFirst()); assertEquals(to, route.getLast());
            assertEquals(Math.abs(from.x() - to.x()) + Math.abs(from.z() - to.z()) + 1, route.size());
            KnownPedestrianNavigation.requireRoute(plane(true, reads), route);
        }
        var opaque = new ProfiledPedestrianGeometry(plane(true, reads), UnitNavigationProfile.VILLAGER, point -> true);
        assertTrue(opaque.uniformSupport(new WorldBounds(0, 0, 16, 16)).isEmpty(), "a separate point predicate supplies no area proof");
        var declared = new ProfiledPedestrianGeometry(plane(true, reads), UnitNavigationProfile.DONKEY, NavigationObstacleView.indexed(Set.of()));
        assertEquals(63, declared.uniformSupport(new WorldBounds(0, 0, 16, 16)).orElseThrow());
    }
    @Test void realProviderWithdrawsTheCertificateForAnUnclassifiedPlayerChangeAndAfterRecovery() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:certified-terrain"), 126,
                FrontierRulesets.installed("frontier-v3-simulation-baseline-r31"));
        var state = FrontierWorldState.initial(bootstrap);
        int x = Math.floorDiv(bootstrap.bounds().minX() + 64, 16) * 16;
        int z = Math.floorDiv(bootstrap.bounds().minZ() + 64, 16) * 16;
        var area = new WorldBounds(x, z, 16, 16); int y = bootstrap.terrain().baselineSupportY();
        assertEquals(y, KnownPedestrianRouteKnowledge.forFrontier(state).forProfile(UnitNavigationProfile.VILLAGER)
                .geometry().uniformSupport(area).orElseThrow());
        var obstruction = new BlockPosition(x + 8, y + 1, z + 8);
        var changed = state.recordPhysicalDelta(new PhysicalDelta(obstruction, PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "isolated-player-change"));
        var original = KnownPedestrianRouteKnowledge.forFrontier(state).forProfile(UnitNavigationProfile.VILLAGER).geometry();
        var oldRevision = original.regionalRevision().orElseThrow();
        var changedGeometry = KnownPedestrianRouteKnowledge.forFrontier(changed).forProfile(UnitNavigationProfile.VILLAGER).geometry();
        assertTrue(changedGeometry.uniformSupport(area).isEmpty());
        var revisited = KnownPedestrianRouteKnowledge.forFrontier(state).forProfile(UnitNavigationProfile.VILLAGER).geometry();
        assertEquals(oldRevision, revisited.regionalRevision().orElseThrow(),
                "reading a predecessor after a successor cannot invent a third physical knowledge version");
        for (int index = 0; index < 12; index++) {
            var successor = state.recordPhysicalDelta(new PhysicalDelta(obstruction.offset(index + 1, 0, 0), PhysicalDeltaKind.UNKNOWN_SCAR,
                    Optional.empty(), Optional.empty(), "distinct-successor-" + index));
            KnownPedestrianRouteKnowledge.forFrontier(successor).forProfile(UnitNavigationProfile.VILLAGER).geometry();
        }
        assertEquals(oldRevision, KnownPedestrianRouteKnowledge.forFrontier(state).forProfile(UnitNavigationProfile.VILLAGER)
                .geometry().regionalRevision().orElseThrow(), "evicting completed snapshots cannot change knowledge identity");
        var codec = new FrontierWorldStateCodec();
        for (var current : List.of(changed, codec.decode(codec.encode(changed)))) {
            var geometry = KnownPedestrianRouteKnowledge.forFrontier(current).forProfile(UnitNavigationProfile.VILLAGER).geometry();
            assertTrue(geometry.uniformSupport(area).isEmpty());
            var result = PedestrianRoutePlanning.calculate(geometry, new SurfaceAnchor(obstruction.offset(0, -1, 0)),
                    SurfaceAnchor.at(x + 9, y, z + 8));
            assertEquals(PedestrianRouteResult.Status.UNKNOWN_GEOMETRY, result.status(), "an unknown physical edit is never assumed clear");
        }
    }
}
