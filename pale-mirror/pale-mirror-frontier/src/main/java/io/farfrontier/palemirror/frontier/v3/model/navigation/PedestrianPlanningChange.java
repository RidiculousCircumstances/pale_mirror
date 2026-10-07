package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.Objects;

/** Rebuildable technical signal, not a movement, arrival or domain outcome receipt. */
public record PedestrianPlanningChange(PedestrianRouteRequest request, Kind kind) {
    public enum Kind { RESULT_AVAILABLE, INVALIDATED }
    public PedestrianPlanningChange { Objects.requireNonNull(request); Objects.requireNonNull(kind); }
}
