package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Geometry policy for resting outside all temporary service buffers, independent of the service's purpose. */
public final class ServiceAreaDestinations {
    private static final int MAX_SEARCH_RADIUS = 32; // Bounded search, never an expanding whole-world scan.
    private record Candidate(SurfaceAnchor surface, long distance, int ordinal) { }
    private ServiceAreaDestinations() { }

    /** Actual-origin escape uses the common declared departure; target and native-route probes stay separate. */
    public static Optional<SurfaceAnchor> select(ServiceClearanceRoutes clearance, SubjectId actorId,
            SurfaceAnchor start, Set<SurfaceAnchor> excluded,
            java.util.function.Predicate<SurfaceAnchor> available,
            java.util.function.Predicate<SurfaceAnchor> reachable) {
        return io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.withService(
                io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianPlanningService.PASSAGE_CLEARANCE,
                () -> selectClearance(clearance::temporary, actorId, start, clearance.knowledge(), excluded,
                        surface -> clearance.publicDestination(surface) && available.test(surface),
                        clearance::allows, reachable));
    }

    public static boolean temporary(ServiceAccessPoint point, SurfaceAnchor surface) {
        return point.boundary().occupied(surface.standingBody()) || point.waitingSurfaces().contains(surface)
                || point.egressSurfaces().contains(surface);
    }

    private static Optional<SurfaceAnchor> selectClearance(java.util.function.Predicate<SurfaceAnchor> temporary, SubjectId actorId,
            SurfaceAnchor start, KnownPedestrianRouteKnowledge knowledge, Set<SurfaceAnchor> excluded,
            java.util.function.Predicate<SurfaceAnchor> available,
            java.util.function.Predicate<List<SurfaceAnchor>> routeAllowed,
            java.util.function.Predicate<SurfaceAnchor> reachable) {
        // Local traffic clearance must not wait for a whole-world hierarchical
        // route when the same geometry already proves a bounded sidestep.
        var local = knowledge.localMovementRoutes(start);
        var nearby = local.keySet().stream().filter(candidate -> !candidate.equals(start))
                .sorted(java.util.Comparator.comparingLong((SurfaceAnchor candidate) ->
                        Math.abs((long) candidate.x() - start.x()) + Math.abs((long) candidate.y() - start.y())
                                + Math.abs((long) candidate.z() - start.z()))
                        .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z)).toList();
        for (var candidate : nearby)
            if (hasStandingClearance(candidate, excluded) && available.test(candidate)
                    && !temporary.test(candidate) && routeAllowed.test(local.get(candidate))
                    && reachable.test(candidate)) return Optional.of(candidate);
        // Parking is a bounded local opportunity. Discover its actual connected
        // component once through shared walking edges before requesting routes.
        // A sealed service aisle must not queue one world search per exterior
        // target and hold every other resident behind that repeated prefix.
        // Exhaustion/unknown space is no global NO_PATH claim: a later ordinary
        // owner review can reconsider the opportunity after geometry/demand changes.
        var connected = Set.copyOf(io.farfrontier.palemirror.frontier.v3.model.geometry.ReachableSurfaceArea.select(
                knowledge.geometry(), List.of(start), start, MAX_SEARCH_RADIUS, 4096, 4096, surface -> true).selected());
        var candidates = new java.util.PriorityQueue<Candidate>(java.util.Comparator.comparingLong(Candidate::distance)
                .thenComparingInt(Candidate::ordinal));
        int ordinal = 0;
        for (int distance = 1; distance <= MAX_SEARCH_RADIUS; distance++) {
            // Expand columns lazily, but rank actual 3-D displacement. A nearby column
            // above a pit is not a nearby sidestep; unseen columns have distance > this ring.
            for (int dx = -distance; dx <= distance; dx++) {
                int dz = distance - Math.abs(dx);
                for (int sign : dz == 0 ? new int[] {1} : new int[] {1, -1}) {
                    for (SurfaceAnchor candidate : knowledge.surfacesAt(start.x() + dx, start.z() + sign * dz)) {
                        long displacement = distance + Math.abs((long) candidate.y() - start.y());
                        if (displacement <= MAX_SEARCH_RADIUS) candidates.add(new Candidate(candidate, displacement, ordinal++));
                    }
                }
            }
            while (!candidates.isEmpty() && candidates.peek().distance() <= distance) {
                var candidate = candidates.remove().surface();
                if (!connected.contains(candidate) || local.containsKey(candidate) || !hasStandingClearance(candidate, excluded)
                        || !available.test(candidate) || temporary.test(candidate)) continue;
                MovementOrder order = new MovementOrder(actorId, actorId, 0, 1L, List.of(candidate),
                        TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
                try {
                    List<SurfaceAnchor> route = knowledge.plannedPath(start, order);
                    if (routeAllowed.test(route) && reachable.test(candidate))
                        return Optional.of(candidate);
                } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                    if (unavailable.status() != io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)
                        throw unavailable;
                    // Unsupported/blocked/unknown columns cannot consume a native candidate probe.
                }
            }
        }
        return Optional.empty();
    }

    /** Idle placement leaves a pedestrian-width gap; this is not a route obstacle policy. */
    public static boolean hasStandingClearance(SurfaceAnchor candidate, Set<SurfaceAnchor> occupied) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            for (int dy = -1; dy <= 1; dy++)
                if (occupied.contains(SurfaceAnchor.at(candidate.x() + dx, candidate.y() + dy, candidate.z() + dz)))
                    return false;
        return true;
    }
}
