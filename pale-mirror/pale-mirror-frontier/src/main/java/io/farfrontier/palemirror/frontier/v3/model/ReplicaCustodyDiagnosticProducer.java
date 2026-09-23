package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** First-boundary stamps for exact replica and custody reconciliation failures. */
public final class ReplicaCustodyDiagnosticProducer {
    private ReplicaCustodyDiagnosticProducer() { }
    public static PhysicalReplicaCustodyPayloads.ProjectionConflictObserved projectionConflict(SubjectId scopeId, long epoch,
            long canonical, long replica, String fingerprint, String provenance) {
        return new PhysicalReplicaCustodyPayloads.ProjectionConflictObserved(scopeId, epoch, canonical, replica, fingerprint, provenance,
                unresolved(scopeId, epoch, canonical, replica, PhysicalCustodyUnresolvedReason.OBSERVATION_MISMATCH).diagnostic());
    }
    public static PhysicalReplicaCustodyPayloads.ReplicaConflictObserved conflict(SubjectId objectId, long canonical, long replica,
                                                                                   String fingerprint, String provenance) {
        return new PhysicalReplicaCustodyPayloads.ReplicaConflictObserved(objectId, canonical, replica, fingerprint, provenance,
                new DiagnosticTuple(DiagnosticReason.REPLICA_CUSTODY_CONFLICT, DiagnosticCategory.RECONCILIATION_CONFLICT,
                        new DiagnosticOwner(DiagnosticOwnerKind.REPLICA_CUSTODY, objectId), new DiagnosticSubject(DiagnosticSubjectKind.REPLICA, objectId), DiagnosticDisposition.INSPECT));
    }
    public static PhysicalReplicaCustodyPayloads.CustodyUnresolved unresolved(SubjectId scopeId, long epoch, long canonical, long replica,
                                                                                PhysicalCustodyUnresolvedReason reason) {
        return new PhysicalReplicaCustodyPayloads.CustodyUnresolved(scopeId, epoch, canonical, replica, reason,
                new DiagnosticTuple(DiagnosticReason.PHYSICAL_CUSTODY_UNRESOLVED, DiagnosticCategory.RECOVERY_UNKNOWN,
                        new DiagnosticOwner(DiagnosticOwnerKind.PHYSICAL_INTENT, scopeId), new DiagnosticSubject(DiagnosticSubjectKind.PHYSICAL_EFFECT, scopeId), DiagnosticDisposition.INSPECT));
    }
}
