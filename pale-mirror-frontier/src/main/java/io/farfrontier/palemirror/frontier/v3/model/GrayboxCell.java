package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One immutable and attributable v3 materialization target. */
public record GrayboxCell(BlockPosition position, SubjectId ownerId, GrayboxMaterial material, GrayboxSemanticPart semanticPart) {
    public GrayboxCell {
        Objects.requireNonNull(position, "graybox position");
        Objects.requireNonNull(ownerId, "graybox owner id");
        Objects.requireNonNull(material, "graybox material");
        Objects.requireNonNull(semanticPart, "graybox semantic part");
    }
}
