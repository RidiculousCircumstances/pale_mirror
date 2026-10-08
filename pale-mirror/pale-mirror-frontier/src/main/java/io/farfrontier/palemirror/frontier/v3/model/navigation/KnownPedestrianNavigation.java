package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BoundedPedestrianApproach;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One known-route boundary for local and distant goals; never reads loaded blocks or awards arrival. */
public final class KnownPedestrianNavigation {
    private KnownPedestrianNavigation() { }

    public static final class RouteUnavailable extends IllegalArgumentException {
        private final PedestrianRouteResult.Status status;
        public RouteUnavailable(String detail) { this(PedestrianRouteResult.Status.NO_PATH, detail); }
        public RouteUnavailable(PedestrianRouteResult.Status status, String detail) { super(detail); this.status = status; }
        public PedestrianRouteResult.Status status() { return status; }
    }

    public static List<SurfaceAnchor> route(FrontierBootstrap bootstrap, SurfaceAnchor start, MovementOrder order,
                                            Set<BlockPosition> occupied,
                                            BoundedPedestrianApproach.SurveyedSurface surveyed) {
        Objects.requireNonNull(bootstrap, "known navigation bootstrap");
        Objects.requireNonNull(start, "known navigation start");
        Objects.requireNonNull(order, "known navigation order");
        occupied = Set.copyOf(Objects.requireNonNull(occupied, "known navigation occupancy"));
        Objects.requireNonNull(surveyed, "known navigation survey");
        if (order.capability() != TraversalCapability.PEDESTRIAN)
            throw new IllegalArgumentException("known pedestrian navigation cannot change actor capability");
        if (!bootstrap.bounds().contains(start.support()))
            throw new RouteUnavailable("known pedestrian start is outside the retained world");
        if (blocked(start, occupied))
            throw new RouteUnavailable("known pedestrian start has no clear support/body column");
        PedestrianRouteGeometry geometry = geometry(bootstrap, occupied, surveyed);
        return route(geometry, start, order);
    }

    public static PedestrianRouteGeometry geometry(FrontierBootstrap bootstrap, Set<BlockPosition> occupied,
                                                    BoundedPedestrianApproach.SurveyedSurface surveyed) {
        return geometry(bootstrap, occupied, surveyed, new Object());
    }
    public static PedestrianRouteGeometry geometry(FrontierBootstrap bootstrap, Set<BlockPosition> occupied,
                                                    BoundedPedestrianApproach.SurveyedSurface surveyed, Object version) {
        var hard = Set.copyOf(occupied);
        return new PedestrianRouteGeometry() {
            @Override public Object version() { return version; }
            @Override public io.farfrontier.palemirror.frontier.v3.model.WorldBounds bounds() { return bootstrap.bounds(); }
            @Override public SurfaceAnchor supportAt(int x, int z) { return surveyed.at(x, z); }
            @Override public boolean blocked(SurfaceAnchor surface) { return KnownPedestrianNavigation.blocked(surface, hard); }
        };
    }

    /** Geometry identity binds reusable search evidence, independently of caller/task identity. */
    public static List<SurfaceAnchor> route(PedestrianRouteGeometry geometry, SurfaceAnchor start, MovementOrder order) {
        return route(geometry, start, order, false);
    }

    /** Admission may wait for runtime-owned work; readiness never grants arrival or authority. */
    public static List<SurfaceAnchor> plannedRoute(PedestrianRouteGeometry geometry, SurfaceAnchor start, MovementOrder order) {
        return route(geometry, start, order, true);
    }

    /** Reuse supported accepted geometry; an advisory hint grants neither movement nor arrival. */
    public static List<SurfaceAnchor> plannedRoute(PedestrianRouteGeometry geometry, SurfaceAnchor start,
                                                  MovementOrder order, List<SurfaceAnchor> hint) {
        Objects.requireNonNull(hint);
        if (order.capability() != TraversalCapability.PEDESTRIAN)
            throw new IllegalArgumentException("known pedestrian navigation cannot change actor capability");
        if (hint.size() > TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("known route hint exceeds bounded movement geometry");
        List<SurfaceAnchor> best = null;
        for (var target : order.legalStations()) {
            int goal = hint.indexOf(target);
            if (goal < 0) continue;
            int join = -1;
            long nearest = Long.MAX_VALUE;
            for (int index = 0; index <= goal; index++) {
                var candidate = hint.get(index);
                long distance = Math.abs((long) start.x() - candidate.x()) + Math.abs((long) start.z() - candidate.z());
                if ((distance == 0 && start.equals(candidate) || distance == 1 && Math.abs((long) start.y() - candidate.y()) <= 1)
                        && distance < nearest) { join = index; nearest = distance; }
            }
            if (join < 0) continue;
            var candidate = new java.util.ArrayList<SurfaceAnchor>();
            if (!start.equals(hint.get(join))) candidate.add(start);
            candidate.addAll(hint.subList(join, goal + 1));
            if (candidate.size() > TimedKnownRoute.MAX_SURFACES) continue;
            // A changed surface invalidates this optimization, not the semantic order.
            if (candidate.stream().anyMatch(surface -> !geometry.bounds().contains(surface.support())
                    || geometry.blocked(surface) || !surface.equals(geometry.supportAt(surface.x(), surface.z())))) continue;
            if (candidate.size() > 1) new PedestrianRouteReceipt(candidate);
            if (best == null || candidate.size() < best.size()) best = List.copyOf(candidate);
        }
        return best == null ? plannedRoute(geometry, start, order) : best;
    }

    private static List<SurfaceAnchor> route(PedestrianRouteGeometry geometry, SurfaceAnchor start, MovementOrder order, boolean deferredPlanning) {
        Objects.requireNonNull(geometry); Objects.requireNonNull(start); Objects.requireNonNull(order);
        if (order.capability() != TraversalCapability.PEDESTRIAN)
            throw new IllegalArgumentException("known pedestrian navigation cannot change actor capability");
        if (!geometry.bounds().contains(start.support()) || geometry.blocked(start))
            throw new RouteUnavailable("known pedestrian start has no clear support/body column");
        List<SurfaceAnchor> best = null;
        PedestrianRouteResult deferred = null;
        PedestrianRouteResult failure = null;
        var pendingRequests = new java.util.ArrayList<PedestrianRouteRequest>();
        for (SurfaceAnchor station : order.legalStations()) {
            if (!geometry.bounds().contains(station.support()) || geometry.blocked(station)) continue;
            PedestrianRouteResult result = deferredPlanning ? PedestrianRoutePlanning.query(geometry, start, station)
                    : PedestrianRoutePlanning.calculate(geometry, start, station);
            if (result.status() == PedestrianRouteResult.Status.PLANNING)
                pendingRequests.add(PedestrianRouteRequest.of(geometry, start, station));
            if (result.status() == PedestrianRouteResult.Status.FOUND) {
                List<SurfaceAnchor> candidate = result.route();
                if (best == null || candidate.size() < best.size()) best = candidate;
            } else {
                failure = result;
                if (result.status() != PedestrianRouteResult.Status.NO_PATH) deferred = result;
            }
        }
        if (best == null && deferred != null) {
            pendingRequests.forEach(PedestrianRoutePlanning::await);
            throw new RouteUnavailable(deferred.status(), deferred.reason());
        }
        if (best == null) throw new RouteUnavailable(failure == null ? "NO_CLEAR_DECLARED_STATION"
                : failure.reason() + "; start=" + start + "; goals=" + order.legalStations());
        return best;
    }

    /** Validate accepted evidence, not optimality: replay never needs a planner/cache. */
    public static void requireRoute(PedestrianRouteGeometry geometry, List<SurfaceAnchor> route) {
        if (route.isEmpty()) throw new IllegalArgumentException("accepted pedestrian route is empty");
        for (SurfaceAnchor surface : route) {
            if (!geometry.bounds().contains(surface.support()) || geometry.blocked(surface)
                    || !surface.equals(geometry.supportAt(surface.x(), surface.z())))
                throw new IllegalArgumentException("accepted pedestrian route differs from current known geometry at " + surface);
        }
    }

    private static boolean blocked(SurfaceAnchor surface, Set<BlockPosition> occupied) {
        return occupied.contains(surface.support()) || occupied.contains(surface.support().offset(0, 1, 0))
                || occupied.contains(surface.support().offset(0, 2, 0));
    }
}
