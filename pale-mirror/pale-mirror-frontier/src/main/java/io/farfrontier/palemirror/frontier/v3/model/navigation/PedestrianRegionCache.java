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
    synchronized PedestrianRegion region(PedestrianRouteGeometry geometry, PedestrianRegion.Tile tile) {
        var regions = views.get(geometry);
        if (regions == null) {
            if (views.size() >= 64) views.clear();
            regions = new LinkedHashMap<>(16, 0.75f, true); views.put(geometry, regions);
        }
        var result = regions.get(tile);
        if (result != null) return result;
        result = new PedestrianRegion(geometry, tile);
        if (views.values().stream().mapToInt(Map::size).sum() >= MAX_REGIONS) {
            var victim = views.values().stream().filter(value -> !value.isEmpty()).findFirst().orElseThrow();
            victim.remove(victim.keySet().iterator().next());
        }
        regions.put(tile, result); return result;
    }
    synchronized void clear() { views.clear(); }
}
