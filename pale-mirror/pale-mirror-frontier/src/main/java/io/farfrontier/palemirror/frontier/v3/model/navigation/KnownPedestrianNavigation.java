package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BoundedPedestrianApproach;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure, bounded local route search over retained knowledge; never reads loaded blocks or awards arrival. */
public final class KnownPedestrianNavigation {
    public enum SearchScope { LOCAL_APPROACH, FRONTIER_JOURNEY }
    private KnownPedestrianNavigation() { }

    public static final class RouteUnavailable extends IllegalArgumentException {
        public RouteUnavailable(String detail) { super(detail); }
    }

    public static List<SurfaceAnchor> route(FrontierBootstrap bootstrap, SurfaceAnchor start, MovementOrder order,
                                            Set<BlockPosition> occupied,
                                            BoundedPedestrianApproach.SurveyedSurface surveyed) {
        return route(bootstrap, start, order, occupied, surveyed, SearchScope.LOCAL_APPROACH);
    }
    public static List<SurfaceAnchor> route(FrontierBootstrap bootstrap, SurfaceAnchor start, MovementOrder order,
            Set<BlockPosition> occupied, BoundedPedestrianApproach.SurveyedSurface surveyed, SearchScope scope) {
        Objects.requireNonNull(bootstrap, "known navigation bootstrap");
        Objects.requireNonNull(start, "known navigation start");
        Objects.requireNonNull(order, "known navigation order");
        occupied = Set.copyOf(Objects.requireNonNull(occupied, "known navigation occupancy"));
        Objects.requireNonNull(surveyed, "known navigation survey");
        Objects.requireNonNull(scope, "known navigation search scope");
        if (order.capability() != TraversalCapability.PEDESTRIAN)
            throw new IllegalArgumentException("known pedestrian navigation cannot change actor capability");
        if (!bootstrap.bounds().contains(start.support()))
            throw new RouteUnavailable("known pedestrian start is outside the retained world");
        if (blocked(start, occupied))
            throw new RouteUnavailable("known pedestrian start has no clear support/body column");
        List<SurfaceAnchor> best = null;
        for (SurfaceAnchor station : order.legalStations()) {
            if (!bootstrap.bounds().contains(station.support()) || blocked(station, occupied)) continue;
            try {
                List<SurfaceAnchor> candidate = scope == SearchScope.FRONTIER_JOURNEY
                        ? BoundedPedestrianApproach.compileJourney(bootstrap, start, station, occupied, surveyed, "known-pedestrian-journey")
                        : BoundedPedestrianApproach.compile(bootstrap, start, station, occupied, surveyed, "known-pedestrian-goal");
                if (best == null || candidate.size() < best.size()) best = candidate;
            } catch (BoundedPedestrianApproach.ApproachUnavailable unavailable) {
                // The caller's station order is the deterministic tie break.
            }
        }
        if (best == null) throw new RouteUnavailable("no bounded route to the declared pedestrian goal");
        return best;
    }

    private static boolean blocked(SurfaceAnchor surface, Set<BlockPosition> occupied) {
        return occupied.contains(surface.support()) || occupied.contains(surface.support().offset(0, 1, 0))
                || occupied.contains(surface.support().offset(0, 2, 0));
    }
}
