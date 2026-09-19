package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Producer-stamped affected identity for a diagnostic tuple. */
public record DiagnosticSubject(DiagnosticSubjectKind kind, SubjectId id) {
    public DiagnosticSubject {
        kind = Objects.requireNonNull(kind, "diagnostic subject kind");
        id = Objects.requireNonNull(id, "diagnostic subject id");
    }
}
