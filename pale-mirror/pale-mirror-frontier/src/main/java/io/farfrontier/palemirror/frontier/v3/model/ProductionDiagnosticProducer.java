package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Named production non-progress boundaries stamp their exact work/facility tuple. */
public enum ProductionDiagnosticProducer {
    INPUT_UNAVAILABLE(ProductionBlockReason.INPUT_UNAVAILABLE),
    OUTPUT_STORAGE_UNAVAILABLE(ProductionBlockReason.OUTPUT_STORAGE_UNAVAILABLE),
    FACILITY_UNAVAILABLE(ProductionBlockReason.FACILITY_UNAVAILABLE),
    WORKER_UNAVAILABLE(ProductionBlockReason.WORKER_UNAVAILABLE),
    FINANCE_UNAVAILABLE(ProductionBlockReason.FINANCE_UNAVAILABLE),
    ROUTE_BLOCKED(ProductionBlockReason.ROUTE_BLOCKED),
    RELATIONSHIP_CONFLICT(ProductionBlockReason.RELATIONSHIP_CONFLICT);

    private final ProductionBlockReason reason;
    ProductionDiagnosticProducer(ProductionBlockReason reason) { this.reason = reason; }
    public ProductionBlockReason reason() { return reason; }
    public ProductionBlocked create(SubjectId settlementId, SubjectId facilityId, SubjectId workId, SubjectId taskId) {
        return new ProductionBlocked(settlementId, facilityId, workId, taskId, reason,
                new DiagnosticTuple(DiagnosticReason.PRODUCTION_BLOCKED, DiagnosticCategory.WAIT_OR_BLOCKED,
                        new DiagnosticOwner(DiagnosticOwnerKind.PRODUCTION_JOB, workId),
                        new DiagnosticSubject(DiagnosticSubjectKind.PRODUCTION_INPUT, facilityId), DiagnosticDisposition.RETRY));
    }
}
