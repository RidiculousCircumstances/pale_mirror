package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianOpenRegionShortcutTest {
    private static PedestrianRouteGeometry geometry(int width, int wall) {
        return geometry(width, wall, PedestrianGeometryRevision.initial());
    }
    private static PedestrianRouteGeometry geometry(int width, int wall, PedestrianGeometryRevision revision) {
        return new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, width, 16); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return surface.x() == wall; }
            public OptionalInt uniformSupport(WorldBounds area) {
                return area.minX() <= wall && area.maxXExclusive() > wall ? OptionalInt.empty() : OptionalInt.of(63);
            }
        };
    }
    private static PedestrianRouteResult finish(HierarchicalPedestrianSearch search) {
        PedestrianRouteResult result = null;
        for (int turn = 0; turn < 2_048; turn++) {
            result = search.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            if (result.status() != PedestrianRouteResult.Status.PLANNING) return result;
        }
        fail("bounded regional search did not terminate: " + result); return null;
    }
    @Test void fortyThousandBlockJourneyUsesCertifiedRegionalLinksAndReturnsOnlyAnAdjacentMovementPrefix() {
        var revision = PedestrianGeometryRevision.initial();
        var geometry = geometry(50_000, -1, revision); var origin = SurfaceAnchor.at(1, 63, 1); var target = SurfaceAnchor.at(40_001, 63, 1);
        var search = new HierarchicalPedestrianSearch(geometry, origin, target, new PedestrianRegionCache(), null, PedestrianRouteExtent.ESTIMATE);
        var estimate = finish(search);
        assertEquals(PedestrianRouteResult.Status.ESTIMATED, estimate.status(), estimate.reason());
        assertTrue(estimate.estimate().orElseThrow().estimatedEdges() >= 40_000);
        assertFalse(search.dependencies().matches(geometry(50_000, 200,
                revision.changed(Set.of(new PedestrianGeometryRevision.Region(12, 0))))),
                "an edit inside a shortcut invalidates its proof, including regions not expanded into cell graphs");
        assertTrue(search.dependencies().matches(geometry(50_000, -1,
                revision.changed(Set.of(new PedestrianGeometryRevision.Region(12, 1000))))));
        var prefix = finish(new HierarchicalPedestrianSearch(geometry, origin, target, new PedestrianRegionCache(), null,
                PedestrianRouteExtent.MOVEMENT_SEGMENT));
        assertEquals(PedestrianRouteResult.Status.SEGMENT_READY, prefix.status(), prefix.reason());
        assertEquals(origin, prefix.route().getFirst());
        assertEquals(PedestrianRouteExtent.MOVEMENT_SEGMENT.maximumSurfaces(), prefix.route().size());
        assertTrue(KnownPedestrianNavigation.supportsRoute(geometry, prefix.route()), "a regional shortcut cannot become a physical jump");
    }
    @Test void uncertifiedWallCannotBeCrossedByARegionalShortcut() {
        var geometry = geometry(800, 200);
        var result = finish(new HierarchicalPedestrianSearch(geometry, SurfaceAnchor.at(1, 63, 1), SurfaceAnchor.at(700, 63, 1),
                new PedestrianRegionCache(), null, PedestrianRouteExtent.ESTIMATE));
        assertEquals(PedestrianRouteResult.Status.NO_PATH, result.status(), "the complete fallback must prove actual disconnection");
    }
}
