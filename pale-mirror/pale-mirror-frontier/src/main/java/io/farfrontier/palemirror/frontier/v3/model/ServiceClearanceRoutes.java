package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;

/** Departure permission only. Never admits a service turn, resource access or a new private destination. */
public final class ServiceClearanceRoutes {
    private final java.util.Map<TerrainColumn, List<ServiceBoundaryProvider.Declaration>> nearby;
    private final SurfaceAnchor origin;
    private final KnownPedestrianRouteKnowledge departure;
    private final KnownPedestrianRouteKnowledge publicGeometry;
    private final FrontierWorldState state;
    private final java.util.Optional<ServicePointId> incumbent;
    private final java.util.Map<SurfaceAnchor, Boolean> publicContacts = new java.util.HashMap<>();
    private final java.util.Map<SubjectId, Boolean> busy = new java.util.HashMap<>();
    private final java.util.Map<SubjectId, ServiceAccessPoint> evaluated = new java.util.HashMap<>();

    private ServiceClearanceRoutes(FrontierWorldState state, SubjectId actor, SurfaceAnchor origin, java.util.Optional<ServicePointId> incumbent) {
        this.origin = origin; this.state = state; this.incumbent = incumbent;
        incumbent.ifPresent(point -> ServiceBoundaryComposition.require(state, point));
        departure = PedestrianLocalDeparture.departure(state, origin).forActor(state, actor);
        publicGeometry = KnownPedestrianRouteKnowledge.forFrontier(state).forActor(state, actor);
        nearby = ServiceBoundaryComposition.nearbyDeclarationIndex(state);
    }

    public static ServiceClearanceRoutes from(FrontierWorldState state, SubjectId actor, SurfaceAnchor actual) {
        return new ServiceClearanceRoutes(state, actor, actual, java.util.Optional.empty());
    }
    public static ServiceClearanceRoutes fromService(FrontierWorldState state, SubjectId actor, SurfaceAnchor actual, ServicePointId point) {
        return new ServiceClearanceRoutes(state, actor, actual, java.util.Optional.of(point));
    }
    public KnownPedestrianRouteKnowledge knowledge() { return departure; }
    public int evaluatedPointCount() { return evaluated.size(); }
    /** Exact indexed candidates only. Distant service geometry cannot affect this contact. */
    public boolean temporary(SurfaceAnchor surface) {
        return nearby.getOrDefault(new TerrainColumn(surface.x(), surface.z()), List.of()).stream()
                .anyMatch(declaration -> declaration.idleClearanceAt(surface)
                        || ServiceAreaDestinations.temporary(evaluated.computeIfAbsent(declaration.pointId(),
                            ignored -> ServiceBoundaryComposition.geometry(state, declaration.identity())), surface));
    }
    public boolean publicDestination(SurfaceAnchor surface) {
        return publicContacts.computeIfAbsent(surface, contact ->
                !FacilityPedestrianContacts.of(state.bootstrap()).noParking().contains(contact.support())
                        && publicGeometry.traversable(List.of(contact)));
    }

    public boolean allows(List<SurfaceAnchor> route) {
        if (route.isEmpty() || !route.getFirst().equals(origin) || !publicDestination(route.getLast())) return false;
        int firstPublic = 0;
        while (firstPublic < route.size() && !publicDestination(route.get(firstPublic))) firstPublic++;
        if (firstPublic == route.size()) return false;
        // Only the declared, geometrically valid private exit may precede public service space.
        if (firstPublic > 0 && !departure.traversable(route.subList(0, firstPublic + 1))) return false;
        var continuation = route.subList(firstPublic, route.size());
        if (!publicGeometry.traversable(continuation)) return false;
        var touched = new java.util.TreeMap<SubjectId, ServiceBoundaryProvider.Declaration>();
        for (var surface : route) for (var declaration : nearby.getOrDefault(
                new TerrainColumn(surface.x(), surface.z()), List.of())) touched.put(declaration.pointId(), declaration);
        for (var point : touched.values()) {
            var boundary = point.boundary();
            // An unused service envelope is ordinary transit, never inventory authority.
            // Keep the incumbent's outward-only crossing and currently demanded throats.
            if (incumbent.filter(point.identity()::equals).isEmpty() && !busy.computeIfAbsent(point.pointId(),
                    id -> !ServiceAccessCapabilities.current(state, id).isEmpty())) continue;
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
