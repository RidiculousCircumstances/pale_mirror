package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Optional;

/** Common route-clock/access boundary rule; no knowledge of a shipment or mining job. */
public final class ServiceApproachSegments {
    private ServiceApproachSegments() { }
    public static List<SurfaceAnchor> bounded(FrontierWorldState state, Optional<ServiceAccessDemand.Identity> access,
                                             List<SurfaceAnchor> route) {
        int end = Math.min(route.size(), TimedKnownRoute.MAX_SURFACES);
        if (access.isPresent() && !ServiceAccessCoordinator.available(state, access.orElseThrow())) {
            var boundary = ServiceAccessCoordinator.boundary(state, access.orElseThrow().pointId());
            for (int index = 1; index < end; index++) if (boundary.occupied(route.get(index).standingBody())) {
                end = index; break;
            }
        }
        return List.copyOf(route.subList(0, end));
    }
    public static void require(FrontierWorldState state, MovementOrder order, Optional<ServiceAccessDemand.Identity> access,
                               List<SurfaceAnchor> route) {
        boolean waiting = access.filter(id -> !ServiceAccessCoordinator.available(state, id)).map(id -> {
            var boundary = ServiceAccessCoordinator.boundary(state, id.pointId());
            return boundary.cleared(route.getLast().standingBody()) && boundary.allowsWaitingRoute(route);
        }).orElse(false);
        if (route.isEmpty() || route.size() > TimedKnownRoute.MAX_SURFACES
                || !waiting && !order.arrivedAt(route.getLast()) && route.size() != TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("COLD segment lacks its bounded goal or declared service boundary");
    }
}
