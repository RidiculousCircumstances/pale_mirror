package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Exact bounded evidence for a changed world cell after physical reconciliation. */
public record PhysicalDelta(BlockPosition position, PhysicalDeltaKind kind, Optional<SubjectId> ownerId,
                            Optional<GrayboxSemanticPart> semanticPart, String cause) {
    public PhysicalDelta {
        Objects.requireNonNull(position, "position"); Objects.requireNonNull(kind, "kind");
        ownerId = Objects.requireNonNull(ownerId, "owner id"); semanticPart = Objects.requireNonNull(semanticPart, "semantic part");
        Objects.requireNonNull(cause, "cause");
        if (cause.isBlank() || cause.length() > 160) throw new IllegalArgumentException("physical delta cause must be bounded");
        if (kind == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS != (ownerId.isPresent() && semanticPart.isPresent())) throw new IllegalArgumentException("physical delta evidence does not match its kind");
    }
}
