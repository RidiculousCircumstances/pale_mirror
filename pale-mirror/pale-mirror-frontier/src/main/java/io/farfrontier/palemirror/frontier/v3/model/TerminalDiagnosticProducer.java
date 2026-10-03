package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Exact terminal producers; free-form presentation text never classifies a diagnostic. */
public final class TerminalDiagnosticProducer {
    private TerminalDiagnosticProducer() { }
    public static OperationFailed operationFailed(SubjectId id, String detail) { return new OperationFailed(id, detail, tuple(DiagnosticReason.OPERATION_FAILED, DiagnosticOwnerKind.ROUTE_OPERATION, DiagnosticSubjectKind.ROUTE_OPERATION, id)); }
    public static SettlementProvisionResolved provisionConflict(SubjectId id) { return new SettlementProvisionResolved(id, SettlementProvisionStatus.CONFLICT,
            java.util.Optional.of(tuple(DiagnosticReason.SETTLEMENT_PROVISION_CONFLICT, DiagnosticOwnerKind.SETTLEMENT_PROVISION, DiagnosticSubjectKind.SETTLEMENT_PROVISION, id))); }
    public static SettlementAssaultTransition assaultConflict(SubjectId id,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) { return new SettlementAssaultTransition(id, SettlementAssaultStatus.CONFLICT,
            java.util.Optional.of(tuple(DiagnosticReason.SETTLEMENT_ASSAULT_CONFLICT, DiagnosticOwnerKind.SETTLEMENT_ASSAULT, DiagnosticSubjectKind.SETTLEMENT_ASSAULT, id)), executions); }
    private static DiagnosticTuple tuple(DiagnosticReason reason, DiagnosticOwnerKind ownerKind, DiagnosticSubjectKind subjectKind, SubjectId id) { return new DiagnosticTuple(reason,
            DiagnosticCategory.RECONCILIATION_CONFLICT, new DiagnosticOwner(ownerKind, id), new DiagnosticSubject(subjectKind, id), DiagnosticDisposition.INSPECT); }
}
