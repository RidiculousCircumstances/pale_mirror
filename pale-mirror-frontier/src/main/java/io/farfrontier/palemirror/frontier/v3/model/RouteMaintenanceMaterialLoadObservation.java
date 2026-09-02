package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact source-slot decrement for one maintenance cargo unit. */
public record RouteMaintenanceMaterialLoadObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId maintenanceId,
                                                      SubjectId cargoId, SubjectId sourceItemId, SubjectId cargoItemId,
                                                      int sourceRemainingCount) implements PhysicalEffectObservation {
    public RouteMaintenanceMaterialLoadObservation {
        Objects.requireNonNull(id, "route maintenance pickup observation"); Objects.requireNonNull(intentId, "route maintenance pickup intent");
        Objects.requireNonNull(maintenanceId, "route maintenance id"); Objects.requireNonNull(cargoId, "route maintenance cargo");
        Objects.requireNonNull(sourceItemId, "route maintenance source item"); Objects.requireNonNull(cargoItemId, "route maintenance cargo item");
        if (sourceRemainingCount < 0) throw new IllegalArgumentException("route maintenance source remainder is negative");
    }
}
