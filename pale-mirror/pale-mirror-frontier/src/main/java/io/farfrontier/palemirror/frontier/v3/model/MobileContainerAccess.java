package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Read-only local interaction geometry for any declared actor-attached container. No mission or food policy. */
public final class MobileContainerAccess {
    private MobileContainerAccess() { }
    public static Optional<SurfaceAnchor> station(FrontierWorldState state, SubjectId actorId, SubjectId containerId,
                                                KnownPedestrianRouteKnowledge knowledge) {
        var surface = state.inventory().surfaces().get(containerId);
        if (surface == null || !(surface.location() instanceof ContainerLocation.Mobile mobile))
            throw new IllegalArgumentException("mobile access needs an explicitly attached container");
        var source = state.actorLocations().get(mobile.actorId()); var recipient = state.actorLocations().get(actorId);
        if (source == null || recipient == null || source.condition().status() != ActorLifeStatus.ALIVE
                || recipient.condition().status() != ActorLifeStatus.ALIVE) return Optional.empty();
        var origin = recipient.supportingSurface(); var center = source.supportingSurface();
        if (currentStation(origin, origin, center, source.body())) return Optional.of(origin);
        var excluded = ServiceDestinationClaims.excludedFor(state, actorId);
        var geometry = knowledge.geometry(); var candidates = new ArrayList<SurfaceAnchor>();
        for (int x = center.x() - 2; x <= center.x() + 2; x++) for (int z = center.z() - 2; z <= center.z() + 2; z++) {
            if (Math.abs(x - center.x()) + Math.abs(z - center.z()) < 2) continue;
            for (var candidate : geometry.surfacesAt(x, z)) {
                if (candidateStation(candidate, center, source.body(), geometry, excluded)) candidates.add(candidate);
            }
        }
        candidates.sort(Comparator.comparingLong((SurfaceAnchor s) -> squared(s.standingBody(), recipient.body())).thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z));
        for (var candidate : candidates) try {
            knowledge.plannedPath(origin, new MovementOrder(containerId, actorId, 0, 1, List.of(candidate), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION));
            return Optional.of(candidate);
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            if (unavailable.status() == PedestrianRouteResult.Status.PLANNING) return Optional.empty();
        }
        return Optional.empty();
    }
    /** Current interaction facts only. A reducer must not repeat the planner's route choice. */
    public static boolean permitsStation(FrontierWorldState state, SubjectId actorId, SubjectId containerId,
                                         SurfaceAnchor station, KnownPedestrianRouteKnowledge knowledge) {
        var surface = state.inventory().surfaces().get(containerId);
        if (surface == null || !(surface.location() instanceof ContainerLocation.Mobile mobile))
            throw new IllegalArgumentException("mobile access needs an explicitly attached container");
        var source = state.actorLocations().get(mobile.actorId()); var recipient = state.actorLocations().get(actorId);
        if (source == null || recipient == null || source.condition().status() != ActorLifeStatus.ALIVE
                || recipient.condition().status() != ActorLifeStatus.ALIVE) return false;
        var origin = recipient.supportingSurface(); var center = source.supportingSurface();
        if (currentStation(station, origin, center, source.body())) return true;
        return candidateStation(station, center, source.body(), knowledge.geometry(), ServiceDestinationClaims.excludedFor(state, actorId));
    }
    private static boolean currentStation(SurfaceAnchor station, SurfaceAnchor origin, SurfaceAnchor center, BodyPosition source) {
        return station.equals(origin) && reachable(origin.standingBody(), source) && !origin.equals(center);
    }
    private static boolean candidateStation(SurfaceAnchor station, SurfaceAnchor center, BodyPosition source,
                                            PedestrianRouteGeometry geometry, Set<SurfaceAnchor> excluded) {
        return Math.abs(station.x() - center.x()) <= 2 && Math.abs(station.z() - center.z()) <= 2
                && Math.abs(station.x() - center.x()) + Math.abs(station.z() - center.z()) >= 2
                && geometry.surfacesAt(station.x(), station.z()).contains(station)
                && geometry.bounds().contains(station.support()) && !geometry.blocked(station)
                && reachable(station.standingBody(), source) && !excluded.contains(station);
    }
    public static boolean reachable(BodyPosition recipient, BodyPosition source) { return squared(recipient, source) <= 9L; }
    private static long squared(BodyPosition a, BodyPosition b) {
        long x = (long) a.x() - b.x(), y = (long) a.y() - b.y(), z = (long) a.z() - b.z();
        return x * x + y * y + z * z;
    }
}
