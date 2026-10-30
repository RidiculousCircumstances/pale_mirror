package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.Objects;

/** One directed geometry-only leg in a bounded calculation batch. No trip or actor admission. */
public record PedestrianRouteLeg(SurfaceAnchor origin, SurfaceAnchor destination) {
    public PedestrianRouteLeg { Objects.requireNonNull(origin); Objects.requireNonNull(destination); }
}
