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
}
