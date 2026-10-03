package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Closed first-boundary producers for a resident's retained migration block. */
public enum ResidentMigrationDiagnosticProducer {
    QUARANTINE(ResidentMigrationBlockReason.QUARANTINE),
    DESTINATION_HOUSING_LOST(ResidentMigrationBlockReason.DESTINATION_HOUSING_LOST),
    ROUTE_OBSTRUCTED(ResidentMigrationBlockReason.ROUTE_OBSTRUCTED);
    private final ResidentMigrationBlockReason reason;
    ResidentMigrationDiagnosticProducer(ResidentMigrationBlockReason reason) { this.reason = reason; }
    public ResidentMigrationBlocked create(io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution) {
        SubjectId residentId = execution.actorId();
        return new ResidentMigrationBlocked(residentId, reason, new DiagnosticTuple(DiagnosticReason.RESIDENT_MIGRATION_BLOCKED,
                DiagnosticCategory.WAIT_OR_BLOCKED, new DiagnosticOwner(DiagnosticOwnerKind.RESIDENT_MIGRATION, residentId),
                new DiagnosticSubject(DiagnosticSubjectKind.RESIDENT_ASSIGNMENT, residentId), DiagnosticDisposition.RETRY), execution);
    }
}
