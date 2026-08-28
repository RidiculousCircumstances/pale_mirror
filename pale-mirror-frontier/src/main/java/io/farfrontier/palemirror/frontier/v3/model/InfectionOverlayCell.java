package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/**
 * One visible marker for an exact four-by-four canonical infection cell.
 *
 * <p>Y is deliberately absent: the NeoForge executor discovers a naturally loaded surface and
 * keeps that resulting position as physical provenance.  The canonical field never copies
 * Minecraft terrain heights.</p>
 */
public record InfectionOverlayCell(InfectionCell cell, int x, int z, InfectionOverlayStage stage) {
    public InfectionOverlayCell {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(stage, "stage");
        int expectedX = Math.addExact(Math.multiplyExact(cell.x(), InfectionCell.BLOCKS), 1);
        int expectedZ = Math.addExact(Math.multiplyExact(cell.z(), InfectionCell.BLOCKS), 1);
        if (x != expectedX || z != expectedZ) throw new IllegalArgumentException("infection overlay marker is not at its cell center");
    }
}
