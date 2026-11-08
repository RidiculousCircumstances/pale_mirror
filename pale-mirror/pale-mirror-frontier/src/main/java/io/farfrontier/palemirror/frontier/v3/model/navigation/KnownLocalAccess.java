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
    private static final BoundedValueMemo<Opportunity, java.util.concurrent.atomic.AtomicReference<Frontier>> FRONTIERS =
            new BoundedValueMemo<>(64);
    /** Geometry identity captures the complete immutable permission/profile view, never merely its hash/version. */
    private record Opportunity(PedestrianRouteGeometry geometry, SurfaceAnchor start, int radius) {
        @Override public boolean equals(Object other) {
            return other instanceof Opportunity value && geometry == value.geometry
                    && start.equals(value.start) && radius == value.radius;
        }
        @Override public int hashCode() { return 31 * (31 * System.identityHashCode(geometry) + start.hashCode()) + radius; }
    }
    private record Frontier(Map<SurfaceAnchor, SurfaceAnchor> parents, List<SurfaceAnchor> visited) {
        Frontier { parents = Map.copyOf(parents); visited = List.copyOf(visited); }
    }
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
        var retained = FRONTIERS.get(new Opportunity(geometry, start, radius), java.util.concurrent.atomic.AtomicReference::new);
        var frontier = retained.get();
        if (frontier != null) {
            // Only walking facts are retained. Current occupancy, destinations and
            // service/owner permissions are reconsidered in their original order.
            for (var candidate : frontier.visited())
                if (!candidate.equals(start) && suitable.test(candidate)) {
                    var route = route(start, candidate, frontier.parents());
                    if (allowed.test(route)) return Optional.of(new PedestrianRouteReceipt(route));
                }
            return Optional.empty();
        }
        return discover(geometry, start, radius, 4096, suitable, allowed,
                explored -> retained.compareAndSet(null, explored));
    }

    private static Optional<PedestrianRouteReceipt> discover(PedestrianRouteGeometry geometry, SurfaceAnchor start,
            int radius, int maximumChecks, java.util.function.Predicate<SurfaceAnchor> suitable,
            java.util.function.Predicate<List<SurfaceAnchor>> allowed) {
        return discover(geometry, start, radius, maximumChecks, suitable, allowed, ignored -> { });
    }

    private static Optional<PedestrianRouteReceipt> discover(PedestrianRouteGeometry geometry, SurfaceAnchor start,
            int radius, int maximumChecks, java.util.function.Predicate<SurfaceAnchor> suitable,
            java.util.function.Predicate<List<SurfaceAnchor>> allowed, java.util.function.Consumer<Frontier> completed) {
        if (!geometry.clearStation(start)) return Optional.empty();
        int checks = 0;
        var parents = new LinkedHashMap<SurfaceAnchor, SurfaceAnchor>();
        var visited = new ArrayList<SurfaceAnchor>();
        var queue = new ArrayDeque<SurfaceAnchor>(); parents.put(start,start); queue.add(start);
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            visited.add(current);
            if (!current.equals(start) && suitable.test(current)) {
                var route = route(start, current, parents);
                if (allowed.test(route)) return Optional.of(new PedestrianRouteReceipt(route));
            }
            for (var direction : FacilityFacing.values()) {
                int x = current.x() + direction.x(), z = current.z() + direction.z();
                if (Math.abs(x-start.x()) > radius || Math.abs(z-start.z()) > radius) continue;
                for (var next : geometry.surfacesAt(x,z)) {
                    if (++checks > maximumChecks) {
                        completed.accept(new Frontier(parents, visited));
                        return Optional.empty();
                    }
                    if (Math.abs(next.y()-start.y()) > radius || parents.containsKey(next)
                            || !geometry.bounds().contains(next.support()) || !geometry.canTraverse(current,next)) continue;
                    parents.put(next,current); queue.addLast(next);
                }
            }
        }
        completed.accept(new Frontier(parents, visited));
        return Optional.empty();
    }

    private static List<SurfaceAnchor> route(SurfaceAnchor start, SurfaceAnchor target,
            Map<SurfaceAnchor, SurfaceAnchor> parents) {
        var route = new ArrayList<SurfaceAnchor>(); var step = target;
        while (!step.equals(start)) { route.add(step); step = parents.get(step); }
        route.add(start); Collections.reverse(route);
        return List.copyOf(route);
    }
}
