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
