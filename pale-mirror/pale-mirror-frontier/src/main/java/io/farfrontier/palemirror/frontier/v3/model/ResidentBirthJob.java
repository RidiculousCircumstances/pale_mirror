package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable canonical commitment for one exact resident after one exact food ration is consumed. */
public record ResidentBirthJob(SubjectId id, SubjectId settlementId, SubjectId householdId, SubjectId foodItemId,
                               SubjectId foodCommitmentId, ResidentProfile resident, BlockPosition position) {
    public ResidentBirthJob {
        Objects.requireNonNull(id, "resident birth job id"); Objects.requireNonNull(settlementId, "resident birth settlement");
        Objects.requireNonNull(householdId, "resident birth household"); Objects.requireNonNull(foodItemId, "resident birth food");
        Objects.requireNonNull(foodCommitmentId, "resident birth food commitment"); Objects.requireNonNull(resident, "resident birth output");
        Objects.requireNonNull(position, "resident birth position");
        if (!id.value().startsWith("job:resident-birth-") || !settlementId.equals(resident.settlementId()) || !householdId.equals(resident.householdId())) {
            throw new IllegalArgumentException("resident birth job must bind one exact output and household");
        }
    }
}
