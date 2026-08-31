package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact loaded-STORE removal evidence before a hive nutrient may become COLD cargo. */
public record HiveNutrientDepartureObservation(PhysicalObservationId id, PhysicalIntentId intentId,
                                               SubjectId transferId, SubjectId cargoId, SubjectId itemId,
                                               int itemCount) implements PhysicalEffectObservation {
    public HiveNutrientDepartureObservation {
        Objects.requireNonNull(id, "observation id"); Objects.requireNonNull(intentId, "intent id");
        Objects.requireNonNull(transferId, "transfer id"); Objects.requireNonNull(cargoId, "cargo id"); Objects.requireNonNull(itemId, "item id");
        if (itemCount < 1 || itemCount > 64) throw new IllegalArgumentException("hive nutrient departure count must be 1..64");
    }
}
