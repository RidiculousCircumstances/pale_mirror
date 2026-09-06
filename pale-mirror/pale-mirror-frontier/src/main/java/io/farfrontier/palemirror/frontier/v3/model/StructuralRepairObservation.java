package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact loaded-world evidence that one supplied item repaired one semantic cell. */
public record StructuralRepairObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId itemId,
                                          BlockPosition position) implements PhysicalEffectObservation {
    public StructuralRepairObservation {
        Objects.requireNonNull(id, "physical observation id");
        Objects.requireNonNull(intentId, "physical observation intent id");
        Objects.requireNonNull(itemId, "repair item id");
        Objects.requireNonNull(position, "repair position");
    }
}
