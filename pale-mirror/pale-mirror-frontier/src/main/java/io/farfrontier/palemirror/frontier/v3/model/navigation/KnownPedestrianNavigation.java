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
        PedestrianRouteGeometry geometry = geometry(bootstrap, occupied, surveyed);
        return route(geometry, start, order);
    }

    public static PedestrianRouteGeometry geometry(FrontierBootstrap bootstrap, Set<BlockPosition> occupied,
                                                    BoundedPedestrianApproach.SurveyedSurface surveyed) {
        return geometry(bootstrap, occupied, surveyed, new Object());
    }
    public static PedestrianRouteGeometry geometry(FrontierBootstrap bootstrap, Set<BlockPosition> occupied,
                                                    BoundedPedestrianApproach.SurveyedSurface surveyed, Object version) {
        return geometry(bootstrap, occupied, surveyed, version, java.util.Optional.empty());
    }
    public static PedestrianRouteGeometry geometry(FrontierBootstrap bootstrap, Set<BlockPosition> occupied,
            BoundedPedestrianApproach.SurveyedSurface surveyed, Object version,
            java.util.Optional<PedestrianGeometryRevision> regionalRevision) {
        var hard = io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet.copyOf(occupied);
        Objects.requireNonNull(regionalRevision);
        var collisions = new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCollisionView() {
            @Override public java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell> cell(BlockPosition position) {
                if (!bootstrap.bounds().contains(position) || !surveyed.blockKnown(position)) return java.util.Optional.empty();
                var observed = surveyed.collisionAt(position);
                if (observed.isPresent()) return observed;
                boolean solid = hard.contains(position) || surveyed.surfacesAt(position.x(), position.z()).stream()
                        .anyMatch(surface -> surface.support().equals(position));
                return java.util.Optional.of(solid ? io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell.SOLID
                        : io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell.AIR);
            }
            @Override public boolean detailedAt(int x, int z) { return surveyed.detailedCollisionAt(x, z); }
        };
        return new PedestrianRouteGeometry() {
            @Override public java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCollisionView> collisions() { return java.util.Optional.of(collisions); }
            @Override public Object version() { return version; }
            @Override public java.util.Optional<PedestrianGeometryRevision> regionalRevision() { return regionalRevision; }
            @Override public io.farfrontier.palemirror.frontier.v3.model.WorldBounds bounds() { return bootstrap.bounds(); }
            @Override public SurfaceAnchor supportAt(int x, int z) { return surveyed.at(x, z); }
            @Override public List<SurfaceAnchor> surfacesAt(int x, int z) { return surveyed.surfacesAt(x, z); }
            @Override public boolean blocked(SurfaceAnchor surface) {
                return supportExcluded(surface) || !clearBody(surface);
            }
            @Override public boolean supportExcluded(SurfaceAnchor surface) {
                return surveyed.excluded(surface);
            }
            @Override public boolean clearBody(SurfaceAnchor surface) {
                if (collisions.detailedAt(surface.x(), surface.z())) return !io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBodyGeometry
                        .contacts(collisions, surface, UnitNavigationProfile.VILLAGER, .5, .5).isEmpty();
                return clearanceKnown(surface) && !hard.contains(surface.support().offset(0, 1, 0))
                        && !hard.contains(surface.support().offset(0, 2, 0)) && surfacesAt(surface.x(), surface.z()).stream()
                        .noneMatch(other -> other.y() > surface.y() && other.y() <= surface.y() + 2);
            }
            @Override public boolean supportAvailable(SurfaceAnchor surface) { return !surveyed.excluded(surface); }
            @Override public boolean clearanceKnown(SurfaceAnchor surface) {
                return collisions.detailedAt(surface.x(), surface.z())
                        ? io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBodyGeometry.known(collisions, surface, UnitNavigationProfile.VILLAGER)
                        : surveyed.clearanceKnown(surface);
            }
            @Override public java.util.Set<BlockPosition> missingGeometry(SurfaceAnchor surface) {
                if (collisions.detailedAt(surface.x(), surface.z())) return io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBodyGeometry
                        .missing(collisions, surface, UnitNavigationProfile.VILLAGER);
                var missing = new java.util.HashSet<BlockPosition>();
                for (int y = 0; y <= 2; y++) {
                    var position = surface.support().offset(0, y, 0);
                    if (!surveyed.blockKnown(position)) missing.add(position);
                }
                return java.util.Set.copyOf(missing);
            }
            @Override public boolean canTraverse(SurfaceAnchor from, SurfaceAnchor to) {
                if (!collisions.detailedAt(from.x(), from.z()) && !collisions.detailedAt(to.x(), to.z()))
                    return PedestrianRouteGeometry.super.canTraverse(from, to);
                if (Math.abs((long) from.x() - to.x()) + Math.abs((long) from.z() - to.z()) != 1
                        || !supportAvailable(from) || !supportAvailable(to)) return false;
                for (var origin : io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBodyGeometry.contacts(collisions, from, UnitNavigationProfile.VILLAGER, .5, .5))
                    for (var target : io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBodyGeometry.contacts(collisions, to, UnitNavigationProfile.VILLAGER, .5, .5))
                        if (io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBodyGeometry.traversable(collisions, UnitNavigationProfile.VILLAGER, origin, target)) return true;
                return false;
            }
            @Override public boolean blockKnown(BlockPosition position) { return surveyed.blockKnown(position); }
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

    /** A proven itinerary with only its nearest bounded part refined. The original order stays current. */
    public static List<SurfaceAnchor> plannedSegment(PedestrianRouteGeometry geometry, SurfaceAnchor start, MovementOrder order) {
        return route(geometry, start, order, true, false, PedestrianRouteExtent.MOVEMENT_SEGMENT);
    }

    /** Geometry-only admission: no fictitious actor/order is needed to request a supported leg. */
    public static List<SurfaceAnchor> plannedBetween(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        var result = queryBetween(geometry, start, target);
        if (result.awaitable())
            PedestrianRoutePlanning.await(PedestrianRouteRequest.of(geometry, start, target));
        if (result.status() != PedestrianRouteResult.Status.FOUND)
            throw new RouteUnavailable(result.status(), result.reason());
        return result.route();
    }

    /** Regional connectivity for alternative ranking. Never exposes cells to a movement executor. */
    public static PedestrianRouteEstimate estimateBetween(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        var result = PedestrianRoutePlanning.query(geometry, start, target, PedestrianRouteExtent.ESTIMATE);
        if (result.awaitable())
            PedestrianRoutePlanning.await(PedestrianRouteRequest.of(geometry, start, target, PedestrianRouteExtent.ESTIMATE));
        if (result.status() != PedestrianRouteResult.Status.ESTIMATED)
            throw new RouteUnavailable(result.status(), result.reason());
        var estimate = result.estimate().orElseThrow();
        if (!estimate.origin().equals(start) || !estimate.destination().equals(target))
            throw new IllegalArgumentException("planner returned an estimate with foreign endpoints");
        return estimate;
    }

    /** Calculation result only. An alternatives owner decides which unresolved requests actually block its result. */
    public static PedestrianRouteResult queryBetween(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        var result = PedestrianRoutePlanning.query(geometry, start, target);
        if (result.status() == PedestrianRouteResult.Status.FOUND && (result.route().isEmpty()
                || !result.route().getFirst().equals(start) || !result.route().getLast().equals(target)))
            throw new IllegalArgumentException("planner returned a route with foreign endpoints");
        if (result.status() == PedestrianRouteResult.Status.FOUND && result.route().size() > TimedKnownRoute.MAX_SURFACES)
            return new PedestrianRouteResult(PedestrianRouteResult.Status.SAFETY_LIMIT, List.of(), result.workUnits(), result.regions(),
                    "ADMISSION_ROUTE_EXCEEDS_BOUNDED_LEG: surfaces=" + result.route().size() + " start=" + start + " target=" + target);
        return result;
    }

    /** Read-only diagnostics can use retained search evidence, never enqueue or finish a calculation. */
    public static List<SurfaceAnchor> retainedRoute(PedestrianRouteGeometry geometry, SurfaceAnchor start, MovementOrder order) {
        return route(geometry, start, order, true, true);
    }

    /** Reuse supported accepted geometry; an advisory hint grants neither movement nor arrival. */
    public static List<SurfaceAnchor> plannedRoute(PedestrianRouteGeometry geometry, SurfaceAnchor start,
                                                  MovementOrder order, List<SurfaceAnchor> hint) {
        return plannedRoute(geometry, start, order, hint, false);
    }
    public static List<SurfaceAnchor> plannedSegment(PedestrianRouteGeometry geometry, SurfaceAnchor start,
                                                    MovementOrder order, List<SurfaceAnchor> hint) {
        return plannedRoute(geometry, start, order, hint, true);
    }
    private static List<SurfaceAnchor> plannedRoute(PedestrianRouteGeometry geometry, SurfaceAnchor start,
                                                   MovementOrder order, List<SurfaceAnchor> hint, boolean segment) {
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
                if ((distance == 0 && start.equals(candidate) || PedestrianEdge.adjacent(start, candidate))
                        && distance < nearest) { join = index; nearest = distance; }
            }
            if (join < 0) continue;
            var candidate = new java.util.ArrayList<SurfaceAnchor>();
            if (!start.equals(hint.get(join))) candidate.add(start);
            candidate.addAll(hint.subList(join, goal + 1));
            if (candidate.size() > TimedKnownRoute.MAX_SURFACES) continue;
            // A changed surface invalidates this optimization, not the semantic order.
            if (candidate.stream().anyMatch(surface -> !geometry.bounds().contains(surface.support())
                    || geometry.blocked(surface) || !geometry.knowsSupport(surface))) continue;
            boolean connected = true;
            for (int index = 1; index < candidate.size(); index++)
                if (!geometry.canTraverse(candidate.get(index - 1), candidate.get(index))) { connected = false; break; }
            if (!connected) continue;
            if (candidate.size() > 1) new PedestrianRouteReceipt(candidate);
            if (best == null || candidate.size() < best.size()) best = List.copyOf(candidate);
        }
        return best != null ? best : segment ? plannedSegment(geometry, start, order) : plannedRoute(geometry, start, order);
    }

    private static List<SurfaceAnchor> route(PedestrianRouteGeometry geometry, SurfaceAnchor start, MovementOrder order, boolean deferredPlanning) {
        return route(geometry, start, order, deferredPlanning, false);
    }

    private static List<SurfaceAnchor> route(PedestrianRouteGeometry geometry, SurfaceAnchor start, MovementOrder order,
            boolean deferredPlanning, boolean retainedOnly) {
        return route(geometry, start, order, deferredPlanning, retainedOnly, PedestrianRouteExtent.COMPLETE);
    }
    private static List<SurfaceAnchor> route(PedestrianRouteGeometry geometry, SurfaceAnchor start, MovementOrder order,
            boolean deferredPlanning, boolean retainedOnly, PedestrianRouteExtent extent) {
        Objects.requireNonNull(geometry); Objects.requireNonNull(start); Objects.requireNonNull(order);
        if (order.capability() != TraversalCapability.PEDESTRIAN)
            throw new IllegalArgumentException("known pedestrian navigation cannot change actor capability");
        if (!geometry.bounds().contains(start.support()) || geometry.clearanceKnown(start) && geometry.blocked(start))
            throw new RouteUnavailable("known pedestrian start has no clear support/body column");
        List<SurfaceAnchor> best = null;
        PedestrianRouteResult deferred = null;
        PedestrianRouteResult failure = null;
        var pendingRequests = new java.util.ArrayList<PedestrianRouteRequest>();
        boolean calculating = false;
        for (SurfaceAnchor station : order.legalStations()) {
            if (!geometry.bounds().contains(station.support()) || geometry.clearanceKnown(station) && geometry.blocked(station)) continue;
            // Explore one unresolved alternative at a time. Still inspect every
            // retained result: an already proven alternative must not wait for it.
            if (deferredPlanning && !retainedOnly && (best != null || calculating)
                    && PedestrianRoutePlanning.peek(geometry, start, station, extent).isEmpty()) continue;
            PedestrianRouteResult result = retainedOnly ? PedestrianRoutePlanning.peek(geometry, start, station, extent)
                    .orElseGet(() -> new PedestrianRouteResult(PedestrianRouteResult.Status.UNKNOWN_GEOMETRY,
                            List.of(), 0, 0, "ROUTE_NOT_CALCULATED"))
                    : deferredPlanning ? PedestrianRoutePlanning.query(geometry, start, station, extent)
                        : PedestrianRoutePlanning.calculate(geometry, start, station);
            calculating |= result.status() == PedestrianRouteResult.Status.PLANNING;
            if (result.awaitable())
                pendingRequests.add(PedestrianRouteRequest.of(geometry, start, station, extent));
            if (result.status() == PedestrianRouteResult.Status.FOUND || result.status() == PedestrianRouteResult.Status.SEGMENT_READY) {
                List<SurfaceAnchor> candidate = result.route();
                if (!candidate.getFirst().equals(start) || candidate.size() > extent.maximumSurfaces()
                        || result.status() == PedestrianRouteResult.Status.FOUND && !candidate.getLast().equals(station)
                        || result.status() == PedestrianRouteResult.Status.SEGMENT_READY
                            && (extent.equals(PedestrianRouteExtent.COMPLETE) || candidate.size() != extent.maximumSurfaces()))
                    throw new IllegalArgumentException("planner returned foreign route endpoints or detail horizon");
                if (best == null || candidate.size() < best.size()) best = candidate;
            } else {
                failure = result;
                if (result.status() != PedestrianRouteResult.Status.NO_PATH) deferred = result;
            }
        }
        if (best == null && deferred != null) {
            if (!retainedOnly) pendingRequests.forEach(PedestrianRoutePlanning::await);
            throw new RouteUnavailable(deferred.status(), deferred.reason());
        }
        if (best == null) throw new RouteUnavailable(failure == null ? "NO_CLEAR_DECLARED_STATION"
                : failure.reason() + "; start=" + start + "; goals=" + order.legalStations());
        return best;
    }

    /** Validate accepted evidence, not optimality: replay never needs a planner/cache. */
    public static boolean supportsRoute(PedestrianRouteGeometry geometry, List<SurfaceAnchor> route) {
        if (route.isEmpty()) return false;
        for (var surface : route) if (!geometry.bounds().contains(surface.support())
                || !geometry.knowsSupport(surface) || geometry.blocked(surface)) return false;
        for (int index = 1; index < route.size(); index++)
            if (!geometry.canTraverse(route.get(index - 1), route.get(index))) return false;
        return true;
    }
    public static void requireRoute(PedestrianRouteGeometry geometry, List<SurfaceAnchor> route) {
        if (route.isEmpty()) throw new IllegalArgumentException("accepted pedestrian route is empty");
        for (SurfaceAnchor surface : route) {
            if (!geometry.bounds().contains(surface.support()) || geometry.blocked(surface)
                    || !geometry.knowsSupport(surface))
                throw new IllegalArgumentException("accepted pedestrian route differs from current known geometry at " + surface);
        }
        for (int index = 1; index < route.size(); index++)
            if (!geometry.canTraverse(route.get(index - 1), route.get(index)))
                throw new IllegalArgumentException("accepted pedestrian route has no clear transition at " + route.get(index));
    }

}
