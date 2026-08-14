package io.farfrontier.palemirror.visuals.genesis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/** Mutable compiler accumulator which is frozen before the catalog is published. */
final class MutableGenesisSlice {
    final long key;
    final Map<Long, CompiledChunkSlice.TerrainColumn> terrain = new LinkedHashMap<>();
    final Map<Long, CompiledChunkSlice.VegetationColumn> vegetation = new LinkedHashMap<>();
    final Map<SurfaceKey, CompiledChunkSlice.SurfaceDecoration> surfaceDecorations = new LinkedHashMap<>();
    final List<CompiledChunkSlice.RailColumn> rails = new ArrayList<>();
    final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

    MutableGenesisSlice(long key) {
        this.key = key;
    }

    void terrain(CompiledChunkSlice.TerrainColumn column) {
        long columnKey = ChunkPos.asLong(column.x(), column.z());
        terrain.merge(columnKey, column, (previous, replacement) ->
                replacement.blendDistance() <= previous.blendDistance() ? replacement : previous);
    }

    void surfaceDecoration(CompiledChunkSlice.SurfaceDecoration decoration) {
        surfaceDecorations.put(new SurfaceKey(decoration.x(), decoration.z(), decoration.offsetY()), decoration);
    }

    CompiledChunkSlice freeze(String catalogHash) {
        String stamp = catalogHash.substring(0, 16) + ":" + Long.toUnsignedString(key, 16) + ":"
                + terrain.size() + ":" + vegetation.size() + ":" + surfaceDecorations.size()
                + ":" + rails.size() + ":" + blocks.size();
        return new CompiledChunkSlice(key, stamp, terrain.values().stream().toList(),
                vegetation.values().stream().toList(), surfaceDecorations.values().stream()
                        .sorted(Comparator.comparingInt(CompiledChunkSlice.SurfaceDecoration::x)
                                .thenComparingInt(CompiledChunkSlice.SurfaceDecoration::z)
                                .thenComparingInt(CompiledChunkSlice.SurfaceDecoration::offsetY))
                        .toList(), rails, blocks);
    }

    private record SurfaceKey(int x, int z, int offsetY) { }
}
