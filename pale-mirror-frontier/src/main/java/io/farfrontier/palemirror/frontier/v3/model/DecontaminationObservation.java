package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact postcondition for one reagent-backed infection-cell physical decontamination. */
public record DecontaminationObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId itemId,
                                         InfectionCell cell, long priorRaw, long remainingRaw) implements PhysicalEffectObservation {
    public DecontaminationObservation {
        Objects.requireNonNull(id, "observation id"); Objects.requireNonNull(intentId, "intent id"); Objects.requireNonNull(itemId, "item id");
        Objects.requireNonNull(cell, "infection cell");
        if (priorRaw <= 0L || remainingRaw < 0L || remainingRaw >= priorRaw) throw new IllegalArgumentException("decontamination intensity result is invalid");
    }
}
