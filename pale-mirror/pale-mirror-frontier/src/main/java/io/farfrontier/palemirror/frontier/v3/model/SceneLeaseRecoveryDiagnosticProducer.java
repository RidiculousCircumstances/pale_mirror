package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;

/** The scene owner, not a recovery renderer, stamps loaded-world recovery absence. */
public final class SceneLeaseRecoveryDiagnosticProducer {
    private SceneLeaseRecoveryDiagnosticProducer() { }

    public static DiagnosticTuple forLease(SceneLeaseId leaseId) {
        return new DiagnosticTuple(DiagnosticReason.SCENE_LEASE_RECOVERY_UNRESOLVED,
                DiagnosticCategory.RECOVERY_UNKNOWN,
                new DiagnosticOwner(DiagnosticOwnerKind.SCENE_LEASE, FrontierSceneLeaseStateSupport.recoveryOwner(leaseId)),
                new DiagnosticSubject(DiagnosticSubjectKind.SCENE_CARRIER, FrontierSceneLeaseStateSupport.recoveryOwner(leaseId)),
                DiagnosticDisposition.INSPECT);
    }
}
