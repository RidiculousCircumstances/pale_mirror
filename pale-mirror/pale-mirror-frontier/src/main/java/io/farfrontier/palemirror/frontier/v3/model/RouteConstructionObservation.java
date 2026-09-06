package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact loaded-world evidence that one supplied item built the next replacement-route cell. */
public record RouteConstructionObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId projectId,
                                           SubjectId itemId, BlockPosition position) implements PhysicalEffectObservation {
    public RouteConstructionObservation {
        Objects.requireNonNull(id, "route construction observation id"); Objects.requireNonNull(intentId, "route construction intent id");
        Objects.requireNonNull(projectId, "route construction project id"); Objects.requireNonNull(itemId, "route construction material id");
        Objects.requireNonNull(position, "route construction position");
    }
}
