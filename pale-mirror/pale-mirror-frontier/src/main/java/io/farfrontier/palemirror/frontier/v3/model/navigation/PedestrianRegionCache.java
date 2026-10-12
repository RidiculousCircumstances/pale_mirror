package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Bounded reusable connected components of immutable geometry; no route, task or access authority. */
final class PedestrianRegionCache {
    private static final int MAX_REGIONS = 2_048;
    // A region contains only supports/components. Its cache must not pin historical world
    // snapshots or their much larger obstacle views through a strong geometry key.
    private final Map<PedestrianRouteGeometry, Map<PedestrianRegion.Tile, PedestrianRegion>> views = new WeakHashMap<>();
    private record RegionalKey(PedestrianRegion.Tile tile, Object revision, io.farfrontier.palemirror.frontier.v3.model.WorldBounds bounds) { }
    private final Map<Object, Map<RegionalKey, PedestrianRegion>> scoped = new WeakHashMap<>();
    synchronized PedestrianRegion region(PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile) {
        if (geometry.regionalRevision().isPresent()) {
            var revision = geometry.regionalRevision().orElseThrow();
            if (scoped.size() >= 64 && !scoped.containsKey(revision.scope())) scoped.remove(scoped.keySet().iterator().next());
            var regions = scoped.computeIfAbsent(revision.scope(), ignored -> new LinkedHashMap<>(16, 0.75f, true));
            var key = new RegionalKey(tile, revision.at(tile.x(), tile.z()), geometry.bounds());
            var prior = regions.get(key);
            if (prior != null) return prior;
            trim(); var result = new PedestrianRegion(geometry, tile); regions.put(key, result); return result;
        }
        var regions = views.get(geometry);
        if (regions == null) {
            if (views.size() >= 64) views.clear();
            regions = new LinkedHashMap<>(16, 0.75f, true); views.put(geometry, regions);
        }
        var result = regions.get(tile);
        if (result != null) return result;
        result = new PedestrianRegion(geometry, tile);
        trim();
        regions.put(tile, result); return result;
    }
    private void trim() {
        if (views.values().stream().mapToInt(Map::size).sum() + scoped.values().stream().mapToInt(Map::size).sum() < MAX_REGIONS) return;
        if (scoped.values().stream().anyMatch(value -> !value.isEmpty())) {
            var victim = scoped.values().stream().filter(value -> !value.isEmpty()).findFirst().orElseThrow();
            victim.remove(victim.keySet().iterator().next());
        } else {
            var victim = views.values().stream().filter(value -> !value.isEmpty()).findFirst().orElseThrow();
            victim.remove(victim.keySet().iterator().next());
        }
    }
    synchronized void clear() { views.clear(); scoped.clear(); }
}
