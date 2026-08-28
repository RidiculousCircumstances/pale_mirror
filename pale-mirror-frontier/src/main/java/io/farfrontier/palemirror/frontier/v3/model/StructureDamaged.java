package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact observed loss of one known v3 structural cell. */
public record StructureDamaged(SubjectId structureId, BlockPosition position, GrayboxSemanticPart semanticPart, String cause)
        implements FrontierPayload {
    public StructureDamaged {
        Objects.requireNonNull(structureId, "structure id");
        Objects.requireNonNull(position, "damage position");
        Objects.requireNonNull(semanticPart, "semantic part");
        Objects.requireNonNull(cause, "damage cause");
        if (cause.isBlank() || cause.length() > 160) throw new IllegalArgumentException("damage cause must be bounded and non-blank");
    }
    @Override public String type() { return "frontier.structure_damaged"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
