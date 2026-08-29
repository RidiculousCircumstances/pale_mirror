package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact loaded-chunk receipt for one owned production stack replacement. */
public record ProductionTransformationObservation(
        PhysicalObservationId id, PhysicalIntentId intentId, SubjectId inputItemId, SubjectId outputItemId,
        int inputCount, int outputCount
) implements PhysicalEffectObservation {
    public ProductionTransformationObservation {
        Objects.requireNonNull(id, "production transformation observation id");
        Objects.requireNonNull(intentId, "production transformation intent id");
        Objects.requireNonNull(inputItemId, "production transformation input id");
        Objects.requireNonNull(outputItemId, "production transformation output id");
        if (inputCount < 1 || inputCount > 64 || outputCount < 1 || outputCount > 64 || inputCount != outputCount) {
            throw new IllegalArgumentException("production transformation receipt must retain one exact 1..64 stack count");
        }
    }
}
