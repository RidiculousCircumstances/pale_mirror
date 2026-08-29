package io.farfrontier.palemirror.frontier.v3.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One complete visible surface patch for an exact four-by-four canonical infection cell.
 *
 * <p>Y is deliberately absent: the NeoForge executor discovers a naturally loaded surface and
 * keeps that resulting position as physical provenance.  The canonical field never copies
 * Minecraft terrain heights.</p>
 */
public record InfectionOverlayCell(InfectionCell cell, InfectionOverlayStage stage) {
    public InfectionOverlayCell {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(stage, "stage");
    }

    /**
     * Terrain-independent columns for the exact canonical cell.  The physical executor discovers
     * each naturally loaded column's actual Y and persists that result as provenance; the pure
     * plan never copies terrain heights into canonical state.
     */
    public List<SurfaceColumn> surfaceColumns() {
        List<SurfaceColumn> columns = new ArrayList<>(InfectionCell.BLOCKS * InfectionCell.BLOCKS);
        int originX = Math.multiplyExact(cell.x(), InfectionCell.BLOCKS);
        int originZ = Math.multiplyExact(cell.z(), InfectionCell.BLOCKS);
        for (int localX = 0; localX < InfectionCell.BLOCKS; localX++) for (int localZ = 0; localZ < InfectionCell.BLOCKS; localZ++) {
            columns.add(new SurfaceColumn(Math.addExact(originX, localX), Math.addExact(originZ, localZ)));
        }
        return List.copyOf(columns);
    }

    /** One X/Z column of the complete patch; its physical surface height is intentionally absent. */
    public record SurfaceColumn(int x, int z) { }
}
