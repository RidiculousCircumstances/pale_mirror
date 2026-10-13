package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Rebuildable read index over immutable delta/route inputs; not movement authority. */
final class KnownRouteBarrierIndex {
    static final int MAX_ROUTES = 128;
    static final int MAX_RETAINED_SURFACES = 65_536;
    private Map<BlockPosition, PhysicalDelta> deltas;
    private final IdentityHashMap<List<SurfaceAnchor>, Integer> barriers = new IdentityHashMap<>();
    private final ArrayDeque<List<SurfaceAnchor>> oldest = new ArrayDeque<>();
    private int retainedSurfaces;

    int first(Map<BlockPosition, PhysicalDelta> current, List<SurfaceAnchor> route) {
        // Canonical maps and retained routes are immutable. Any delta-map replacement
        // invalidates even a cached negative result; no revision/cause is guessed.
        if (deltas != current) {
            deltas = current;
            barriers.clear(); oldest.clear(); retainedSurfaces = 0;
        }
        if (current.isEmpty()) return -1;
        var cached = barriers.get(route);
        if (cached != null) return cached;
        int barrier = scan(current, route);
        if (route.size() <= MAX_RETAINED_SURFACES) {
            while (barriers.size() >= MAX_ROUTES || retainedSurfaces + route.size() > MAX_RETAINED_SURFACES) {
                var retired = oldest.removeFirst();
                barriers.remove(retired); retainedSurfaces -= retired.size();
            }
            barriers.put(route, barrier); oldest.addLast(route); retainedSurfaces += route.size();
        }
        return barrier;
    }

    private static int scan(Map<BlockPosition, PhysicalDelta> deltas, List<SurfaceAnchor> route) {
        for (int index = 1; index < route.size(); index++) {
            var support = route.get(index).support();
            if (deltas.containsKey(support) || deltas.containsKey(support.offset(0, 1, 0))
                    || deltas.containsKey(support.offset(0, 2, 0))) return index;
        }
        return -1;
    }

    int retainedRoutes() { return barriers.size(); }
    int retainedSurfaces() { return retainedSurfaces; }
}
