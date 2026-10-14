package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.util.*;

/** Bounded reusable geometry proofs. No caller, reservation, goal or arrival authority. */
final class PedestrianCorridorCache {
    private static final int MAX_CORRIDORS = 128, MAX_SURFACES = 131_072, MAX_DEPENDENCIES = 16_384;
    private record Key(Object geometryScope, WorldBounds bounds, PedestrianRegion.Tile from, PedestrianRegion.Tile to) { }
    record Corridor(List<SurfaceAnchor> route, Map<PedestrianRegion.Tile, Object> dependencies) { }
    private final Map<Key, Corridor> corridors = new LinkedHashMap<>(16, 0.75f, true);
    private int surfaces, dependencies;
    private long hits;

    synchronized Corridor find(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        var key = key(geometry, start, target);
        var result = key == null ? null : corridors.get(key);
        if (result != null) hits++;
        return result; // The consumer validates every dependency cooperatively before use.
    }

    synchronized void remember(PedestrianRouteGeometry geometry, PedestrianRouteResult result,
                               PedestrianRegionDependencies proof) {
        if (result.status() != PedestrianRouteResult.Status.FOUND || result.route().size() < 2
                || result.route().size() > MAX_SURFACES || proof.revisions().size() > MAX_DEPENDENCIES) return;
        var key = key(geometry, result.route().getFirst(), result.route().getLast());
        if (key == null) return;
        var old = corridors.get(key);
        // Repeated local joins must not grow a trunk by appending a new detour
        // on every use. New geometry still replaces an obsolete proof.
        if (old != null && old.dependencies().equals(proof.revisions())
                && old.route().size() <= result.route().size()) return;
        corridors.remove(key);
        if (old != null) subtract(old);
        while (!corridors.isEmpty() && (corridors.size() >= MAX_CORRIDORS
                || surfaces + result.route().size() > MAX_SURFACES
                || dependencies + proof.revisions().size() > MAX_DEPENDENCIES)) {
            var iterator = corridors.entrySet().iterator(); var removed = iterator.next().getValue();
            iterator.remove(); subtract(removed);
        }
        // Result and dependency records already own immutable collections. Retain
        // their references instead of copying the distant route on publication.
        var corridor = new Corridor(result.route(), proof.revisions());
        corridors.put(key, corridor); surfaces += corridor.route().size(); dependencies += corridor.dependencies().size();
    }
    private static Key key(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        return geometry.regionalRevision().map(revision -> new Key(revision.geometryScope(), geometry.bounds(),
                PedestrianRegion.Tile.at(start), PedestrianRegion.Tile.at(target))).orElse(null);
    }
    private void subtract(Corridor corridor) { surfaces -= corridor.route().size(); dependencies -= corridor.dependencies().size(); }
    synchronized long hits() { return hits; }
    synchronized void clear() { corridors.clear(); surfaces = dependencies = 0; }
}
