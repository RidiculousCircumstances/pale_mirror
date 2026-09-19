package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Producer-stamped owner identity for a diagnostic tuple. */
public record DiagnosticOwner(DiagnosticOwnerKind kind, SubjectId id) {
    public DiagnosticOwner {
        kind = Objects.requireNonNull(kind, "diagnostic owner kind");
        id = Objects.requireNonNull(id, "diagnostic owner id");
    }
}
