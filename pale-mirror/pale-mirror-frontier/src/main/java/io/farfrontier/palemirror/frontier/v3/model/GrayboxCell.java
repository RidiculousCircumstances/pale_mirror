package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** One immutable and attributable v3 materialization target. */
public record GrayboxCell(BlockPosition position, PhysicalDeltaSemanticTarget semanticTarget, GrayboxMaterial material, GrayboxSemanticPart semanticPart) {
    public GrayboxCell {
        Objects.requireNonNull(position, "graybox position");
        Objects.requireNonNull(semanticTarget, "graybox semantic target");
        Objects.requireNonNull(material, "graybox material");
        Objects.requireNonNull(semanticPart, "graybox semantic part");
    }
    /** Read-only exact subject label; dispatch authority remains {@link #semanticTarget()}. */
    public SubjectId ownerId() { return semanticTarget.subjectId(); }
}
