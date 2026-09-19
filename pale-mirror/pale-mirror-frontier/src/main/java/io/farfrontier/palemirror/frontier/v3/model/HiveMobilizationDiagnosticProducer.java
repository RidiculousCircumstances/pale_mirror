package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Closed first-boundary producers for exact cocoon-release conflicts. */
public enum HiveMobilizationDiagnosticProducer {
    COCOON_CHANGED(HiveMobilizationConflictReason.COCOON_CHANGED),
    DEPARTURE_UNAVAILABLE(HiveMobilizationConflictReason.DEPARTURE_UNAVAILABLE),
    ASSEMBLY_PATH_BLOCKED(HiveMobilizationConflictReason.ASSEMBLY_PATH_BLOCKED),
    UNKNOWN_AFTER_RESTART(HiveMobilizationConflictReason.UNKNOWN_AFTER_RESTART);
    private final HiveMobilizationConflictReason reason;
    HiveMobilizationDiagnosticProducer(HiveMobilizationConflictReason reason) { this.reason = reason; }
    public HiveMobilizationConflicted create(SubjectId mobilizationId) {
        return new HiveMobilizationConflicted(mobilizationId, reason, new DiagnosticTuple(DiagnosticReason.HIVE_MOBILIZATION_CONFLICT,
                DiagnosticCategory.RECONCILIATION_CONFLICT, new DiagnosticOwner(DiagnosticOwnerKind.HIVE_MOBILIZATION, mobilizationId),
                new DiagnosticSubject(DiagnosticSubjectKind.HIVE_COCOON, mobilizationId), DiagnosticDisposition.INSPECT));
    }
    public HiveMobilizationConflicted create(SubjectId mobilizationId, HiveAssemblyBlockage blockage) {
        return new HiveMobilizationConflicted(mobilizationId, reason, java.util.Optional.of(blockage), new DiagnosticTuple(DiagnosticReason.HIVE_MOBILIZATION_CONFLICT,
                DiagnosticCategory.RECONCILIATION_CONFLICT, new DiagnosticOwner(DiagnosticOwnerKind.HIVE_MOBILIZATION, mobilizationId),
                new DiagnosticSubject(DiagnosticSubjectKind.HIVE_COCOON, blockage.actorId()), DiagnosticDisposition.INSPECT));
    }
}
