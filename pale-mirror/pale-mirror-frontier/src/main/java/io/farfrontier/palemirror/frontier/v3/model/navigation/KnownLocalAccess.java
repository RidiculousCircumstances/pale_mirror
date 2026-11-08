package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Bounded positive local access proof. Stops at the nearest eligible contact; a miss
 * is never proof of global disconnection and does not replace cooperative planning. */
public final class KnownLocalAccess {
    // This is an optional positive shortcut, not the complete path planner.
    // A miss (including exhausted work) falls through to cooperative routing.
    private static final int MAX_EDGE_CHECKS = 128;
    private static final ImmutableResultView<Query, Optional<PedestrianRouteReceipt>> CACHE = new ImmutableResultView<>(64);
    private record Query(SurfaceAnchor start, Set<SurfaceAnchor> targets, int radius) {
        Query { targets = Set.copyOf(targets); }
    }
    private KnownLocalAccess() { }
    public static Optional<PedestrianRouteReceipt> nearest(PedestrianRouteGeometry geometry, SurfaceAnchor start,
            Set<SurfaceAnchor> targets, int radius) {
        if (radius < 1 || radius > 16 || targets.size() > 4096) throw new IllegalArgumentException("unbounded local access query");
        return CACHE.get(List.of(geometry), new Query(start, targets, radius), () -> {
            if (targets.isEmpty() || targets.contains(start) || targets.stream().noneMatch(target ->
                    Math.abs((long) target.x() - start.x()) <= radius
                            && Math.abs((long) target.y() - start.y()) <= radius
                            && Math.abs((long) target.z() - start.z()) <= radius)) return Optional.empty();
            return discover(geometry, start, radius, MAX_EDGE_CHECKS, targets::contains, route -> true);
        });
    }

    /** Optional local opportunity, with the same walking edges and a finite work
     * bound. Empty (including unknown/exhausted) is never a global NO_PATH proof;
     * the owner keeps its ordinary reconsideration instead of acquiring a route wait. */
    public static Optional<PedestrianRouteReceipt> opportunity(PedestrianRouteGeometry geometry, SurfaceAnchor start,
            int radius, java.util.function.Predicate<SurfaceAnchor> suitable,
            java.util.function.Predicate<List<SurfaceAnchor>> allowed) {
        if (radius < 1 || radius > 32) throw new IllegalArgumentException("unbounded local opportunity");
        return discover(geometry, start, radius, 4096, suitable, allowed);
    }

    private static Optional<PedestrianRouteReceipt> discover(PedestrianRouteGeometry geometry, SurfaceAnchor start,
            int radius, int maximumChecks, java.util.function.Predicate<SurfaceAnchor> suitable,
            java.util.function.Predicate<List<SurfaceAnchor>> allowed) {
        if (!geometry.clearStation(start)) return Optional.empty();
        int checks = 0;
        var parents = new LinkedHashMap<SurfaceAnchor, SurfaceAnchor>();
        var queue = new ArrayDeque<SurfaceAnchor>(); parents.put(start,start); queue.add(start);
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            if (!current.equals(start) && suitable.test(current)) {
                var route = new ArrayList<SurfaceAnchor>(); var step = current;
                while (!step.equals(start)) { route.add(step); step = parents.get(step); }
                route.add(start); Collections.reverse(route);
                if (allowed.test(List.copyOf(route))) return Optional.of(new PedestrianRouteReceipt(route));
            }
            for (var direction : FacilityFacing.values()) {
                int x = current.x() + direction.x(), z = current.z() + direction.z();
                if (Math.abs(x-start.x()) > radius || Math.abs(z-start.z()) > radius) continue;
                for (var next : geometry.surfacesAt(x,z)) {
                    if (++checks > maximumChecks) return Optional.empty();
                    if (Math.abs(next.y()-start.y()) > radius || parents.containsKey(next)
                            || !geometry.bounds().contains(next.support()) || !geometry.canTraverse(current,next)) continue;
                    parents.put(next,current); queue.addLast(next);
                }
            }
        }
        return Optional.empty();
    }
}
