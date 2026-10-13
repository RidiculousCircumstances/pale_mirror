package io.farfrontier.palemirror.frontier.v3.model.extraction;

import java.util.Objects;

/** Finite connected inclusion in the authored initial face, not an infinite ore generator. */
public record ExtractionPocketProfile(int producerPeriod, int firstDepth, int depthCells,
                                     int firstRow, int rowCells, BlockExtraction.Definition material) {
    public ExtractionPocketProfile {
        Objects.requireNonNull(material);
        if (producerPeriod < 1 || producerPeriod > 12 || firstDepth < 0 || firstRow < 0
                || depthCells < 1 || rowCells < 1 || firstDepth + depthCells > 32 || firstRow + rowCells > 32)
            throw new IllegalArgumentException("invalid finite deposit inclusion");
    }
    public boolean contains(int producer, int depth, int row) {
        return producer % producerPeriod == 0 && depth >= firstDepth && depth < firstDepth + depthCells
                && row >= firstRow && row < firstRow + rowCells;
    }
    public String canonicalText() {
        return producerPeriod + "," + firstDepth + "," + depthCells + "," + firstRow + "," + rowCells
                + ":" + material.id() + ":" + ExtractionRules.blockText(material.before()) + ":"
                + ExtractionRules.blockText(material.after()) + ":" + material.toolKind() + ":" + material.lootTable()
                + ":" + material.coldOutput();
    }
}
