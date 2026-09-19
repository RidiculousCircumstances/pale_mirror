package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Exact bounded evidence for a changed world cell after physical reconciliation. */
public record PhysicalDelta(BlockPosition position, PhysicalDeltaKind kind, Optional<PhysicalDeltaSemanticTarget> semanticTarget,
                            Optional<GrayboxSemanticPart> semanticPart, String cause) {
    public PhysicalDelta {
        Objects.requireNonNull(position, "position"); Objects.requireNonNull(kind, "kind");
        semanticTarget = Objects.requireNonNull(semanticTarget, "semantic target"); semanticPart = Objects.requireNonNull(semanticPart, "semantic part");
        Objects.requireNonNull(cause, "cause");
        if (cause.isBlank() || cause.length() > 160) throw new IllegalArgumentException("physical delta cause must be bounded");
        if (kind == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS != (semanticTarget.isPresent() && semanticPart.isPresent())) throw new IllegalArgumentException("physical delta evidence does not match its kind");
    }
    /** Read-only subject label; authority remains the typed semantic target. */
    public Optional<SubjectId> ownerId() { return semanticTarget.map(PhysicalDeltaSemanticTarget::subjectId); }
}
