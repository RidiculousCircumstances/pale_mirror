package io.farfrontier.palemirror.visuals.genesis;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable work owned by exactly one naturally-generating chunk. */
public record CompiledChunkSlice(long chunkKey, String stamp, List<TerrainColumn> terrain,
                                 List<VegetationColumn> vegetation,
                                 List<RailColumn> rails, Map<BlockPos, BlockState> blocks) {
    public CompiledChunkSlice {
        terrain = List.copyOf(terrain);
        vegetation = List.copyOf(vegetation);
        rails = List.copyOf(rails);
        blocks = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(blocks));
    }

    public record TerrainColumn(int x, int z, int targetY, BlockState surface, BlockState foundation) { }

    /** Cleanup-only column; unlike TerrainColumn it never grades or claims the ground. */
    public record VegetationColumn(int x, int z, int baseY) { }

    public record RailColumn(BlockPos rail, BlockState railState, BlockState support) { }
}
