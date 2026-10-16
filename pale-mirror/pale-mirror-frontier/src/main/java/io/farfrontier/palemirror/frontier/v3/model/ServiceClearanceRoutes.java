package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;

/** Departure permission only. Never admits a service turn, resource access or a new private destination. */
public final class ServiceClearanceRoutes {
    private final SurfaceAnchor origin;
    private final KnownPedestrianRouteKnowledge departure;
    private final KnownPedestrianRouteKnowledge publicGeometry;
    private final List<ServiceAccessPoint> points;

    private ServiceClearanceRoutes(FrontierWorldState state, SubjectId actor, SurfaceAnchor origin) {
        this.origin = origin;
        departure = PedestrianLocalDeparture.departure(state, origin).forActor(state, actor);
        publicGeometry = KnownPedestrianRouteKnowledge.forFrontier(state).forActor(state, actor);
        points = ServiceBoundaryComposition.geometries(state);
    }

    public static ServiceClearanceRoutes from(FrontierWorldState state, SubjectId actor, SurfaceAnchor actual) {
        return new ServiceClearanceRoutes(state, actor, actual);
    }
    public KnownPedestrianRouteKnowledge knowledge() { return departure; }
    public List<ServiceAccessPoint> points() { return points; }
    public boolean publicDestination(SurfaceAnchor surface) { return publicGeometry.traversable(List.of(surface)); }

    public boolean allows(List<SurfaceAnchor> route) {
        if (route.isEmpty() || !route.getFirst().equals(origin) || !publicDestination(route.getLast())) return false;
        int firstPublic = 0;
        while (firstPublic < route.size() && !publicDestination(route.get(firstPublic))) firstPublic++;
        if (firstPublic == route.size()) return false;
        // Only the declared, geometrically valid private exit may precede public service space.
        if (firstPublic > 0 && !departure.traversable(route.subList(0, firstPublic + 1))) return false;
        var continuation = route.subList(firstPublic, route.size());
        for (var point : points) {
            var boundary = point.boundary();
            if (boundary.allowsWaitingRoute(route)) continue;
            // The first public contact may be inside its service throat. This is outward
            // clearance, not permission to cross another service zone or re-enter after leaving.
            if (firstPublic == 0 || !boundary.occupied(continuation.getFirst().standingBody())
                    || !boundary.allowsWaitingRoute(continuation)) return false;
            boolean entered = false;
            for (var surface : route.subList(0, firstPublic + 1)) {
                boolean inside = boundary.occupied(surface.standingBody());
                if (entered && !inside) return false;
                entered |= inside;
            }
        }
        return true;
    }
}
