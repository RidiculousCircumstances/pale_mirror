package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable refusal to advance a journey; it never rewrites the resident's current location. */
public record ResidentMigrationBlocked(SubjectId residentId, ResidentMigrationBlockReason reason,
                                       DiagnosticTuple diagnostic,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId executionId) implements FrontierPayload {
    public ResidentMigrationBlocked { Objects.requireNonNull(residentId, "migration resident"); Objects.requireNonNull(reason, "migration block reason"); diagnostic = Objects.requireNonNull(diagnostic, "migration block diagnostic");
        TransitActivityCapability.requireDeclared(residentId, executionId);
        if (diagnostic.reason() != DiagnosticReason.RESIDENT_MIGRATION_BLOCKED || !diagnostic.owner().id().equals(residentId)
                || !diagnostic.subject().id().equals(residentId)) throw new IllegalArgumentException("migration block has a foreign diagnostic tuple"); }
    @Override public String type() { return "frontier.resident_migration_blocked"; }
}
