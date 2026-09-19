package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Closed first-boundary producers for retained hive nutrient-transfer blocks. */
public enum HiveNutrientDiagnosticProducer {
    ENDPOINT_MATERIALIZED(HiveNutrientTransferBlockReason.ENDPOINT_MATERIALIZED),
    TARGET_SLOT_UNAVAILABLE(HiveNutrientTransferBlockReason.TARGET_SLOT_UNAVAILABLE),
    CARGO_CUSTODY_LOST(HiveNutrientTransferBlockReason.CARGO_CUSTODY_LOST);
    private final HiveNutrientTransferBlockReason reason;
    HiveNutrientDiagnosticProducer(HiveNutrientTransferBlockReason reason) { this.reason = reason; }
    public HiveNutrientTransferBlocked create(SubjectId transferId) {
        return new HiveNutrientTransferBlocked(transferId, reason, new DiagnosticTuple(DiagnosticReason.HIVE_NUTRIENT_BLOCKED,
                DiagnosticCategory.WAIT_OR_BLOCKED, new DiagnosticOwner(DiagnosticOwnerKind.HIVE_NUTRIENT_TRANSFER, transferId),
                new DiagnosticSubject(DiagnosticSubjectKind.HIVE_TRANSFER, transferId), DiagnosticDisposition.RETRY));
    }
}
