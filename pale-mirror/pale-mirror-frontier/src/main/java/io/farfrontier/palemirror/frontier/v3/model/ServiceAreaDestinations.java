package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Geometry policy for resting outside all temporary service buffers, independent of the service's purpose. */
public final class ServiceAreaDestinations {
    private static final int MAX_SEARCH_RADIUS = 32; // Bounded search, never an expanding whole-world scan.
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
        // Optional parking cannot hold the canonical predecessor behind one
        // global route request per rejected destination. Reuse the common bounded
        // positive access proof, stopping at the first actually permitted route.
        return io.farfrontier.palemirror.frontier.v3.model.navigation.KnownLocalAccess.opportunity(
                knowledge.geometry(), start, MAX_SEARCH_RADIUS,
                candidate -> hasStandingClearance(candidate, excluded) && available.test(candidate) && !temporary.test(candidate),
                route -> routeAllowed.test(route) && reachable.test(route.getLast()))
                .map(receipt -> receipt.route().getLast());
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
