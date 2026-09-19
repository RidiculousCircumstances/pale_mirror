package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** A verified lack of input, work-capable resident, usable output storage or facility capacity; it never creates stock. */
public record ProductionBlocked(
        SubjectId settlementId, SubjectId facilityId, SubjectId workId, ProductionBlockReason reason, DiagnosticTuple diagnostic
) implements FrontierPayload {
    public ProductionBlocked {
        Objects.requireNonNull(settlementId, "settlement id");
        Objects.requireNonNull(facilityId, "facility id");
        Objects.requireNonNull(workId, "work id");
        Objects.requireNonNull(reason, "production block reason");
        diagnostic = Objects.requireNonNull(diagnostic, "production block diagnostic");
        if (diagnostic.reason() != DiagnosticReason.PRODUCTION_BLOCKED || !diagnostic.owner().id().equals(workId)
                || !diagnostic.subject().id().equals(facilityId)) throw new IllegalArgumentException("production block has a foreign diagnostic tuple");
    }
    @Override public String type() { return "frontier.production_blocked"; }
}
