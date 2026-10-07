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
        if (reachable(origin.standingBody(), source.body()) && !origin.equals(center)) return Optional.of(origin);
        var excluded = ServiceDestinationClaims.excludedFor(state, actorId);
        var geometry = knowledge.geometry(); var candidates = new ArrayList<SurfaceAnchor>();
        for (int x = center.x() - 2; x <= center.x() + 2; x++) for (int z = center.z() - 2; z <= center.z() + 2; z++) {
            if (Math.abs(x - center.x()) + Math.abs(z - center.z()) < 2) continue;
            var candidate = geometry.supportAt(x, z);
            if (candidate != null && geometry.bounds().contains(candidate.support()) && !geometry.blocked(candidate)
                    && reachable(candidate.standingBody(), source.body()) && !excluded.contains(candidate)) candidates.add(candidate);
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
    public static boolean reachable(BodyPosition recipient, BodyPosition source) { return squared(recipient, source) <= 9L; }
    private static long squared(BodyPosition a, BodyPosition b) {
        long x = (long) a.x() - b.x(), y = (long) a.y() - b.y(), z = (long) a.z() - b.z();
        return x * x + y * y + z * z;
    }
}
