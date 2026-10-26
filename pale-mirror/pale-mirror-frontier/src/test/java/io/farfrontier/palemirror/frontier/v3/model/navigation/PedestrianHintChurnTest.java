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
    @Test void aLongCachedDetourCannotReplaceANearbyOrdinaryRoute() {
        var revision = PedestrianGeometryRevision.initial();
        PedestrianRouteGeometry ground = new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public Optional<PedestrianGeometryRevision> regionalRevision() { return Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(0, 0, 96, 2_112); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
        };
        var cells = new ArrayList<SurfaceAnchor>();
        for (int z = 1; z <= 2_100; z++) cells.add(SurfaceAnchor.at(1, 63, z));
        for (int x = 2; x <= 70; x++) cells.add(SurfaceAnchor.at(x, 63, 2_100));
        for (int z = 2_099; z >= 1; z--) cells.add(SurfaceAnchor.at(70, 63, z));
        var portals = new ArrayList<PedestrianItineraryCache.Portal>();
        var proof = new LinkedHashMap<PedestrianRegion.Tile, Object>();
        for (int i = 0; i < cells.size(); i++) {
            var tile = PedestrianRegion.Tile.at(cells.get(i)); proof.put(tile, revision.at(tile.x(), tile.z()));
            if (i > 0 && !tile.equals(PedestrianRegion.Tile.at(cells.get(i-1))))
                portals.add(new PedestrianItineraryCache.Portal(cells.get(i-1), cells.get(i), 0, 0));
        }
        var itineraries = new PedestrianItineraryCache();
        itineraries.remember(ground, cells.getLast(), portals, proof);
        var corridors = new PedestrianCorridorCache();
        corridors.remember(ground, new PedestrianRouteResult(PedestrianRouteResult.Status.FOUND, cells, 0, 0, "proven detour"),
                new PedestrianRegionDependencies(ground.bounds(), revision.scope(), revision.geometryScope(), proof));
        for (boolean corridor : List.of(false, true)) {
            var search = new HierarchicalPedestrianSearch(ground, cells.getFirst(), cells.getLast(), new PedestrianRegionCache(),
                    corridor ? corridors : null, PedestrianRouteExtent.COMPLETE, corridor ? null : itineraries);
            PedestrianRouteResult result = null;
            for (int turn = 0; turn < 4_096; turn++) {
                search.prepareReuse(); result = search.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
                if (result.status() != PedestrianRouteResult.Status.PLANNING) break;
            }
            assertEquals(PedestrianRouteResult.Status.FOUND, result.status());
            KnownPedestrianNavigation.requireRoute(ground, result.route());
            assertTrue(result.route().size() < 100, "a reusable hint is optional; its distant detour must fall back to regional search");
            var accepted = result; assertDoesNotThrow(() -> new PedestrianRouteReceipt(accepted.route()));
        }
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
