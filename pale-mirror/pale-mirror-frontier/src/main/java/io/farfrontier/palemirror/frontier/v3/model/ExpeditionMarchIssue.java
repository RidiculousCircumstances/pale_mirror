package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Typed evidence tied to one member's next immutable edge; it never supplies an alternate route. */
public record ExpeditionMarchIssue(ExpeditionMarchIssueKind kind, SubjectId memberId, TraversalEdgeId edgeId,
                                   int expectedCursor) {
    public ExpeditionMarchIssue {
        kind = Objects.requireNonNull(kind, "expedition issue kind"); memberId = Objects.requireNonNull(memberId, "expedition issue member");
        edgeId = Objects.requireNonNull(edgeId, "expedition issue edge");
        if (expectedCursor < 0) throw new IllegalArgumentException("expedition issue cursor must be non-negative");
    }
}
