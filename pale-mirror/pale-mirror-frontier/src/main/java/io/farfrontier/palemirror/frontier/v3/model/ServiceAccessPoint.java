package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;

/** Geometry of a service resource. Contains no meal, recipe, queue or job policy. */
public record ServiceAccessPoint(SubjectId facilityId, SubjectId settlementId, SurfaceAnchor station,
                                 ServiceAccessBoundary boundary, List<SurfaceAnchor> waitingSurfaces) {
    public ServiceAccessPoint {
        Objects.requireNonNull(facilityId); Objects.requireNonNull(settlementId);
        Objects.requireNonNull(station); Objects.requireNonNull(boundary);
        waitingSurfaces = List.copyOf(waitingSurfaces);
        if (!boundary.occupied(station.standingBody())
                || waitingSurfaces.stream().anyMatch(surface -> boundary.occupied(surface.standingBody())))
            throw new IllegalArgumentException("service waiting positions must be outside its access boundary");
    }
    public boolean occupancyChanged(BodyPosition previous, BodyPosition observed) {
        return !previous.equals(observed) && (boundary.occupied(previous) || boundary.occupied(observed));
    }
}
