package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** First-boundary inventory producers; no inventory conflict kind is used to classify a diagnostic later. */
public enum InventoryDiagnosticProducer {
    PLAYER_EXPECTED_SLOT_MISSING(1, InventoryConflictKind.MISSING),
    PLAYER_FOREIGN_OR_DUPLICATE_SLOT(2, InventoryConflictKind.FOREIGN_OR_DUPLICATE),
    HOPPER_FOREIGN_OR_DUPLICATE_SLOT(3, InventoryConflictKind.FOREIGN_OR_DUPLICATE),
    RESOURCE_SITE_DEFERRED_RECEIPT_MISSING(4, InventoryConflictKind.MISSING);

    private final int wireTag;
    private final InventoryConflictKind conflictKind;
    InventoryDiagnosticProducer(int wireTag, InventoryConflictKind conflictKind) { this.wireTag = wireTag; this.conflictKind = conflictKind; }
    public int wireTag() { return wireTag; }
    public InventoryConflict create(SubjectId id, SubjectId subjectId, SubjectId containerId, int slot) {
        DiagnosticTuple tuple = new DiagnosticTuple(DiagnosticReason.INVENTORY_CONFLICT, DiagnosticCategory.RECONCILIATION_CONFLICT,
                new DiagnosticOwner(DiagnosticOwnerKind.INVENTORY_CUSTODY, containerId),
                new DiagnosticSubject(DiagnosticSubjectKind.INVENTORY_SLOT, id), DiagnosticDisposition.INSPECT);
        return new InventoryConflict(id, subjectId, containerId, slot, conflictKind, tuple);
    }
}
