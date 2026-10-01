package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.Set;

/** Physical occupancy boundary of one shared service point, independent of its job lifecycle. */
public record ServiceAccessBoundary(Set<SurfaceAnchor> occupiedSurfaces) {
    public ServiceAccessBoundary {
        occupiedSurfaces = Set.copyOf(Objects.requireNonNull(occupiedSurfaces, "service access surfaces"));
        if (occupiedSurfaces.isEmpty()) throw new IllegalArgumentException("service access needs a physical boundary");
    }

    public boolean occupied(BodyPosition body) {
        return body != null && occupiedSurfaces.contains(body.supportingSurface());
    }

    public boolean cleared(BodyPosition body) {
        return body != null && !occupied(body);
    }

    /** An unauthorized occupant may leave the throat, but may not re-enter it en route to waiting. */
    public boolean allowsWaitingRoute(java.util.List<SurfaceAnchor> route) {
        boolean outside = false;
        for (SurfaceAnchor surface : route) {
            boolean inside = occupied(surface.standingBody());
            if (outside && inside) return false;
            if (!inside) outside = true;
        }
        return outside;
    }
}
