package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianGoalFieldsTest {
    @Test void manyOriginsShareOneExactFieldAndChangedImageRepairsWithoutMutatingTheOldPath() {
        var revision = PedestrianGeometryRevision.initial(); var cache = new PedestrianRegionCache();
        var geometry = geometry(revision, false); var region = region(cache, geometry);
        var target = SurfaceAnchor.at(12, 63, 5); var source = SurfaceAnchor.at(1, 63, 5);
        var first = cache.refine(geometry, region, source, target); settle(first);
        KnownPedestrianNavigation.requireRoute(geometry, first.result());
        var warm = cache.refine(geometry, region, SurfaceAnchor.at(0, 63, 0), target); settle(warm);
        for (int z = 0; z < 12; z++) {
            var next = cache.refine(geometry, region, SurfaceAnchor.at(0, 63, z), target); settle(next);
            KnownPedestrianNavigation.requireRoute(geometry, next.result());
        }
        long work = cache.goalFieldDiagnostic().get("work");
        for (int z = 0; z < 12; z++) {
            var repeated = cache.refine(geometry, region, SurfaceAnchor.at(0, 63, z), target); settle(repeated);
        }
        assertEquals(work, cache.goalFieldDiagnostic().get("work"), "different origins consume already settled labels without repeating search");
        assertTrue(cache.sharedLocal(geometry, source, target) != null);
        var changed = geometry(revision.changed(Set.of(new PedestrianGeometryRevision.Region(0, 0))), true);
        assertNull(cache.sharedLocal(changed, source, target), "a stale field cannot bypass current geometry");
        var repaired = cache.refine(changed, region(cache, changed), source, target); settle(repaired);
        KnownPedestrianNavigation.requireRoute(changed, repaired.result());
        assertTrue(repaired.result().stream().noneMatch(p -> p.y() == 63 && p.x() == 5 && p.z() < 10));
        assertEquals(1, cache.goalFieldDiagnostic().get("repairs"));
        assertTrue(cache.goalFieldDiagnostic().get("seededLabels") > 0);
        assertEquals(12, first.result().size(), "another image cannot mutate an accepted path");
        var upper = cache.refine(changed, region(cache, changed), SurfaceAnchor.at(1, 67, 5), SurfaceAnchor.at(12, 67, 5));
        settle(upper); assertTrue(upper.result().stream().allMatch(p -> p.y() == 67));
        cache.clear(); assertEquals(0, cache.goalFieldDiagnostic().get("fields"));
    }
    private static PedestrianRegion region(PedestrianRegionCache cache, PedestrianRouteGeometry geometry) {
        PedestrianRegion found = null;
        for (int turn = 0; turn < 100 && found == null; turn++) found = cache.region(geometry, new PedestrianRegion.Tile(0, 0), 1024).region();
        return Objects.requireNonNull(found);
    }
    private static void settle(PedestrianRegion.Refinement refinement) {
        for (int turn = 0; turn < 20_000 && refinement.result() == null; turn++) assertTrue(refinement.advance(17) <= 17);
        assertNotNull(refinement.result());
    }
    private static PedestrianRouteGeometry geometry(PedestrianGeometryRevision revision, boolean wall) {
        return new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 16, 16); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public List<SurfaceAnchor> surfacesAt(int x, int z) { return List.of(SurfaceAnchor.at(x, 63, z), SurfaceAnchor.at(x, 67, z)); }
            public boolean blocked(SurfaceAnchor p) { return wall && p.y() == 63 && p.x() == 5 && p.z() < 10; }
        };
    }
}
