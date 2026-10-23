package io.farfrontier.palemirror.frontier.v3.model.extraction;

import java.util.Objects;

/** Explicit known top layer and its ordinary removal recipe, not permission to erase overburden. */
public record ExtractionSurfaceCut(int topY, long revision, BlockExtraction.Definition cover) {
    public ExtractionSurfaceCut {
        Objects.requireNonNull(cover);
        if (topY < -2048 || topY > 2047 || revision < 1 || cover.coldOutput().size() != 1
                || !cover.after().kind().equals("minecraft:air"))
            throw new IllegalArgumentException("invalid known excavation surface cut");
    }
    public String canonicalText() {
        return topY + ":" + revision + ":" + cover.id() + ":" + ExtractionRules.blockText(cover.before())
                + ":" + ExtractionRules.blockText(cover.after()) + ":" + cover.toolRequirement() + ":" + cover.lootTable()
                + ":" + cover.coldOutput().stream().map(output -> output.itemKind() + ":" + output.quantity())
                    .collect(java.util.stream.Collectors.joining(";"));
    }
}
