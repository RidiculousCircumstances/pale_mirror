package io.farfrontier.palemirror.frontier.v3.model.geometry;

import java.util.List;
import java.util.Objects;

/** Authored topsoil mosaic shared by unloaded knowledge and the physical generator. */
public record TerrainSurfacePalette(int tileSize, int xStride, int zStride, List<NavigationBlockState> blocks) {
    public TerrainSurfacePalette {
        blocks = List.copyOf(blocks);
        if (tileSize < 1 || tileSize > 64 || blocks.isEmpty() || blocks.size() > 64)
            throw new IllegalArgumentException("invalid bounded terrain surface palette");
        blocks.forEach(Objects::requireNonNull);
    }
    public NavigationBlockState at(int x, int z) {
        long tile = (long) Math.floorDiv(x, tileSize) * xStride + (long) Math.floorDiv(z, tileSize) * zStride;
        return blocks.get((int) Math.floorMod(tile, blocks.size()));
    }
    public String canonicalText() {
        return tileSize + ":" + xStride + ":" + zStride + ":" + blocks.stream()
                .map(block -> block.kind() + new java.util.TreeMap<>(block.properties())).toList();
    }
}
