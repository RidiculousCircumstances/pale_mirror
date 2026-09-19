package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/**
 * Nominal owner for the recovery binding that the replica-custody reducer moves into
 * ambiguity.  This is an admission declaration, not a classifier of recovery state.
 */
public final class FencedRecoveryDiagnosticProducer {
    private FencedRecoveryDiagnosticProducer() { }

    public static FencedRecoveryPayloads.Ambiguous ambiguous(SubjectId bindingId, long expectedEpoch, String reason,
                                                               FencedRecoveryDisposition action) {
        return new FencedRecoveryPayloads.Ambiguous(bindingId, expectedEpoch, reason, action, tuple(bindingId));
    }

    static void requireExact(SubjectId bindingId, DiagnosticTuple diagnostic) {
        if (diagnostic.reason() != DiagnosticReason.FENCED_RECOVERY_AMBIGUOUS
                || !diagnostic.owner().id().equals(bindingId) || !diagnostic.subject().id().equals(bindingId)) {
            throw new IllegalArgumentException("fenced recovery ambiguity has a foreign diagnostic tuple");
        }
    }

    private static DiagnosticTuple tuple(SubjectId bindingId) {
        return new DiagnosticTuple(DiagnosticReason.FENCED_RECOVERY_AMBIGUOUS,
                DiagnosticCategory.RECOVERY_UNKNOWN,
                new DiagnosticOwner(DiagnosticOwnerKind.REPLICA_CUSTODY, bindingId),
                new DiagnosticSubject(DiagnosticSubjectKind.PHYSICAL_EFFECT, bindingId),
                DiagnosticDisposition.INSPECT);
    }
}
