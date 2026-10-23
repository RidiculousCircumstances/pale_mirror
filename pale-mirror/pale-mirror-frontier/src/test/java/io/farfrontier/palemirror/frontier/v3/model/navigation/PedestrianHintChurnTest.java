package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianHintChurnTest {
    @Test void publishingUnrelatedHintsCannotRepeatedlyPreemptAnAdmittedSearch() {
        var revision = PedestrianGeometryRevision.initial();
        PedestrianRouteGeometry geometry = new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 256, 256); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
        };
        var stable = new PedestrianItineraryCache(); var changing = new PedestrianItineraryCache();
        publish(stable, geometry, revision, 0); publish(changing, geometry, revision, 0);
        var first = search(geometry, stable); var second = search(geometry, changing);
        PedestrianRouteResult expected = null, actual = null;
        for (int turn = 0; turn < 4096; turn++) {
            publish(changing, geometry, revision, turn % 2);
            expected = advance(first); actual = advance(second);
            if (expected.status() != PedestrianRouteResult.Status.PLANNING) break;
        }
        assertEquals(PedestrianRouteResult.Status.ESTIMATED, expected.status());
        assertEquals(expected.status(), actual.status(), "unrelated cache publication must not extend an admitted search indefinitely");
        assertEquals(expected.estimate(), actual.estimate());
    }
    private static HierarchicalPedestrianSearch search(PedestrianRouteGeometry geometry, PedestrianItineraryCache cache) {
        return new HierarchicalPedestrianSearch(geometry, SurfaceAnchor.at(1, 63, 1), SurfaceAnchor.at(240, 63, 240),
                new PedestrianRegionCache(), null, PedestrianRouteExtent.ESTIMATE, cache);
    }
    private static PedestrianRouteResult advance(HierarchicalPedestrianSearch search) {
        search.prepareReuse(); var allowance = new AtomicInteger(128);
        return search.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK, () -> allowance.getAndDecrement() > 0);
    }
    private static void publish(PedestrianItineraryCache cache, PedestrianRouteGeometry geometry,
                                PedestrianGeometryRevision revision, int variant) {
        var portal = new PedestrianItineraryCache.Portal(SurfaceAnchor.at(15, 63, 1), SurfaceAnchor.at(16, 63, 1), 0, 0);
        var proof = new LinkedHashMap<PedestrianRegion.Tile, Object>();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 8 + variant; z++)
            proof.put(new PedestrianRegion.Tile(x, z), revision.at(x, z));
        cache.remember(geometry, portal.entrance(), List.of(portal), proof);
    }
}
