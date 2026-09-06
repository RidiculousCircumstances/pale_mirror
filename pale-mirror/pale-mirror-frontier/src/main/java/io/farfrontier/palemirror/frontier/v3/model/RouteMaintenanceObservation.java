package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact observed repair of one retained route-loss cell. */
public record RouteMaintenanceObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId maintenanceId,
                                          SubjectId itemId, BlockPosition position) implements PhysicalEffectObservation {
    public RouteMaintenanceObservation {
        Objects.requireNonNull(id, "route maintenance observation"); Objects.requireNonNull(intentId, "route maintenance intent");
        Objects.requireNonNull(maintenanceId, "route maintenance id"); Objects.requireNonNull(itemId, "route maintenance item");
        Objects.requireNonNull(position, "route maintenance position");
    }
}
