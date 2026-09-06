package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable permit for one exact resident, held until physical food consumption is observed. */
public record ResidentBirthJob(SubjectId id, SubjectId settlementId, SubjectId householdId, SubjectId foodItemId,
                               PhysicalIntentId consumptionIntentId, ResidentProfile resident, BlockPosition position) {
    public ResidentBirthJob {
        Objects.requireNonNull(id, "resident birth job id"); Objects.requireNonNull(settlementId, "resident birth settlement");
        Objects.requireNonNull(householdId, "resident birth household"); Objects.requireNonNull(foodItemId, "resident birth food");
        Objects.requireNonNull(consumptionIntentId, "resident birth consumption intent"); Objects.requireNonNull(resident, "resident birth output");
        Objects.requireNonNull(position, "resident birth position");
        if (!id.value().startsWith("job:resident-birth-") || !settlementId.equals(resident.settlementId()) || !householdId.equals(resident.householdId())) {
            throw new IllegalArgumentException("resident birth job must bind one exact output and household");
        }
    }
}
