package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.Objects;

/** Connected regional itinerary and approximate distance, never a movement or provisioning proof.
 * Obstacles inside a region can lengthen its eventual detailed route. */
public record PedestrianRouteEstimate(SurfaceAnchor origin, SurfaceAnchor destination, long estimatedEdges) {
    public PedestrianRouteEstimate {
        Objects.requireNonNull(origin); Objects.requireNonNull(destination);
        if (estimatedEdges < 0 || !origin.equals(destination) && estimatedEdges == 0)
            throw new IllegalArgumentException("invalid regional distance estimate");
    }
}
