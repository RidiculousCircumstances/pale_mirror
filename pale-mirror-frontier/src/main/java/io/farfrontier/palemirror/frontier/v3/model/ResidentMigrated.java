package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable relocation of one exact resident into a named existing destination household. */
public record ResidentMigrated(SubjectId residentId, SubjectId destinationHouseholdId, SubjectId destinationSettlementId,
                               BlockPosition destination) implements FrontierPayload {
    public ResidentMigrated {
        Objects.requireNonNull(residentId, "resident id"); Objects.requireNonNull(destinationHouseholdId, "destination household id");
        Objects.requireNonNull(destinationSettlementId, "destination settlement id"); Objects.requireNonNull(destination, "destination");
    }
    @Override public String type() { return "frontier.resident_migrated"; }
}
