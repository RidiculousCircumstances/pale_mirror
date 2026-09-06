package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** A verified lack of input, work-capable resident, usable output storage or facility capacity; it never creates stock. */
public record ProductionBlocked(
        SubjectId settlementId, SubjectId facilityId, SubjectId workId, ProductionBlockReason reason
) implements FrontierPayload {
    public ProductionBlocked {
        Objects.requireNonNull(settlementId, "settlement id");
        Objects.requireNonNull(facilityId, "facility id");
        Objects.requireNonNull(workId, "work id");
        Objects.requireNonNull(reason, "production block reason");
    }
    @Override public String type() { return "frontier.production_blocked"; }
}
