package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Exact declared diagnostic authority for a restart-unknown ambient actor lease. */
public final class AmbientLeaseDiagnosticProducer {
    private AmbientLeaseDiagnosticProducer() { }

    public static DiagnosticTuple restartAbsence(SubjectId actorId) {
        return new DiagnosticTuple(DiagnosticReason.AMBIENT_LEASE_RESTART_ABSENCE, DiagnosticCategory.RECOVERY_UNKNOWN,
                new DiagnosticOwner(DiagnosticOwnerKind.AMBIENT_LEASE, actorId),
                new DiagnosticSubject(DiagnosticSubjectKind.AMBIENT_ACTOR, actorId), DiagnosticDisposition.INSPECT);
    }
}
