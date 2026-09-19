package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Complete nominal diagnostic authority, validated before it can be persisted or rendered. */
public record DiagnosticTuple(DiagnosticReason reason, DiagnosticCategory category, DiagnosticOwner owner,
                              DiagnosticSubject subject, DiagnosticDisposition disposition) {
    public DiagnosticTuple {
        reason = Objects.requireNonNull(reason, "diagnostic reason");
        category = Objects.requireNonNull(category, "diagnostic category");
        owner = Objects.requireNonNull(owner, "diagnostic owner");
        subject = Objects.requireNonNull(subject, "diagnostic subject");
        disposition = Objects.requireNonNull(disposition, "diagnostic disposition");
        if (reason.category() != category || reason.ownerKind() != owner.kind()
                || reason.subjectKind() != subject.kind() || reason.disposition() != disposition) {
            throw new IllegalArgumentException("diagnostic tuple does not match its registered nominal reason");
        }
    }
}
