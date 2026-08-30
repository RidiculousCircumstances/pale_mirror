package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact physical extraction of one maintenance unit into one COLD construction cargo. */
public record RouteConstructionMaterialLoadObservation(PhysicalObservationId id, PhysicalIntentId intentId,
                                                       SubjectId projectId, SubjectId cargoId, SubjectId sourceItemId,
                                                       SubjectId cargoItemId, int sourceRemainingCount) implements PhysicalEffectObservation {
    public RouteConstructionMaterialLoadObservation {
        Objects.requireNonNull(id, "route construction material observation id");
        Objects.requireNonNull(intentId, "route construction material observation intent id");
        Objects.requireNonNull(projectId, "route construction material project id");
        Objects.requireNonNull(cargoId, "route construction material cargo id");
        Objects.requireNonNull(sourceItemId, "route construction material source item id");
        Objects.requireNonNull(cargoItemId, "route construction material cargo item id");
        if (sourceRemainingCount < 0 || sourceRemainingCount > 63) throw new IllegalArgumentException("route construction material remainder is invalid");
    }
}
