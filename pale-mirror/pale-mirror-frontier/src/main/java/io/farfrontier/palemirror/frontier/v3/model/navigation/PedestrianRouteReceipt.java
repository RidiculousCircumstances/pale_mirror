package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.List;

/** Accepted bounded leg; its enclosing event owns execution/goal/time, not an ephemeral search token. */
public record PedestrianRouteReceipt(List<SurfaceAnchor> route) {
    public PedestrianRouteReceipt {
        route = List.copyOf(route);
        if (route.size() < 2 || route.size() > TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("accepted route needs one bounded movement leg");
        for (int i = 1; i < route.size(); i++) {
            var a = route.get(i - 1); var b = route.get(i);
            if (Math.abs((long) a.x() - b.x()) + Math.abs((long) a.z() - b.z()) != 1L
                    || Math.abs((long) a.y() - b.y()) > 1L)
                throw new IllegalArgumentException("accepted route contains a non-pedestrian edge");
        }
    }
}
