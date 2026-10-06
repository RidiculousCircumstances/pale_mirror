package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.List;
import java.util.Objects;

/** Search readiness is not arrival. Resource/permit/activity outcomes belong to their owners. */
public record PedestrianRouteResult(Status status, List<SurfaceAnchor> route, long workUnits,
                                    int regions, String reason) {
    public enum Status { PLANNING, FOUND, NO_PATH, UNKNOWN_GEOMETRY, SAFETY_LIMIT }
    public PedestrianRouteResult {
        Objects.requireNonNull(status); Objects.requireNonNull(reason);
        route = List.copyOf(route);
        if (workUnits < 0 || regions < 0 || (status == Status.FOUND) != !route.isEmpty())
            throw new IllegalArgumentException("route result has inconsistent readiness/evidence");
    }
}
