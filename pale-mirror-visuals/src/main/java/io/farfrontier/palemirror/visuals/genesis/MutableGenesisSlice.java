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
    final Map<BlockPos, CompiledChunkSlice.AuthoredDecoration> decorations = new LinkedHashMap<>();
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

    void decoration(CompiledChunkSlice.AuthoredDecoration decoration) {
        CompiledChunkSlice.AuthoredDecoration prior = decorations.putIfAbsent(decoration.position(), decoration);
        if (prior != null && !prior.state().equals(decoration.state())) {
            throw new IllegalStateException("Conflicting authored decoration at " + decoration.position()
                    + ": " + prior.state() + " vs " + decoration.state());
        }
    }

    /** Explicit final-paint operation inside one already-owned semantic surface compiler. */
    void replaceDecoration(CompiledChunkSlice.AuthoredDecoration decoration) {
        decorations.put(decoration.position(), decoration);
    }

    CompiledChunkSlice freeze(String catalogHash) {
        String stamp = catalogHash.substring(0, 16) + ":" + Long.toUnsignedString(key, 16) + ":"
                + terrain.size() + ":" + vegetation.size() + ":" + decorations.size()
                + ":" + rails.size() + ":" + blocks.size();
        return new CompiledChunkSlice(key, stamp, terrain.values().stream().toList(),
                vegetation.values().stream().toList(), decorations.values().stream()
                        .sorted(Comparator.comparingInt((CompiledChunkSlice.AuthoredDecoration value) ->
                                        value.position().getX())
                                .thenComparingInt(value -> value.position().getZ())
                                .thenComparingInt(value -> value.position().getY()))
                        .toList(), rails, blocks);
    }
}
