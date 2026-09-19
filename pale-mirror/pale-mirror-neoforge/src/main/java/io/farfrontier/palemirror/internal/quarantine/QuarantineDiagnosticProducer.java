package io.farfrontier.palemirror.internal.quarantine;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticCategory;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticDisposition;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticOwner;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticOwnerKind;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticReason;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubject;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubjectKind;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticTuple;

/** The legacy boundary stamps quarantine at observation; retained text is presentation only. */
final class QuarantineDiagnosticProducer {
    private static final SubjectId FRONTIER_RUNTIME = new SubjectId("frontier:runtime");
    private QuarantineDiagnosticProducer() { }
    static DiagnosticTuple stamp(String recordId) {
        SubjectId subject = new SubjectId(recordId);
        return new DiagnosticTuple(DiagnosticReason.FRONTIER_QUARANTINE, DiagnosticCategory.CANONICAL_INVARIANT_FAILURE,
                new DiagnosticOwner(DiagnosticOwnerKind.FRONTIER_INSTANCE, FRONTIER_RUNTIME),
                new DiagnosticSubject(DiagnosticSubjectKind.FRONTIER_INSTANCE, subject), DiagnosticDisposition.QUARANTINE);
    }
}
