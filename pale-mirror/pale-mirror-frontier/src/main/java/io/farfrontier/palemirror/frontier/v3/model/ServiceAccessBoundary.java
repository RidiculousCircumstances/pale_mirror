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

    /** Spatial ordering only, not a reachability or route-length claim. */
    public long distanceFrom(BodyPosition body) {
        Objects.requireNonNull(body, "service visitor position");
        return occupiedSurfaces.stream().map(SurfaceAnchor::standingBody).mapToLong(target ->
                Math.abs((long) target.x() - body.x()) + Math.abs((long) target.y() - body.y())
                        + Math.abs((long) target.z() - body.z())).min().orElseThrow();
    }

    /** First entry after departure; an approach is distinct from the final admitted entry. */
    public int firstEntry(java.util.List<SurfaceAnchor> route) {
        for (int index = 1; index < route.size(); index++)
            if (occupied(route.get(index).standingBody())) return index;
        return -1;
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
