package io.farfrontier.palemirror.visuals.genesis;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable work owned by exactly one naturally-generating chunk. */
public record CompiledChunkSlice(long chunkKey, String stamp, List<TerrainColumn> terrain,
                                 List<VegetationColumn> vegetation,
                                 List<SurfaceDecoration> surfaceDecorations,
                                 List<RailColumn> rails, Map<BlockPos, BlockState> blocks) {
    public CompiledChunkSlice {
        terrain = List.copyOf(terrain);
        vegetation = List.copyOf(vegetation);
        surfaceDecorations = List.copyOf(surfaceDecorations);
        rails = List.copyOf(rails);
        blocks = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(blocks));
    }

    /**
     * Exact authored surface when {@code blendDistance == 0}; otherwise the
     * natural column is clamped into a one-block-per-column transition toward
     * {@code targetY}. This keeps local pads flat without leaving cut-earth
     * walls at their perimeter.
     */
    public record TerrainColumn(int x, int z, int targetY, BlockState surface, BlockState foundation,
                                int blendDistance) {
        public TerrainColumn {
            if (blendDistance < 0) throw new IllegalArgumentException("blendDistance must be non-negative");
        }

        public TerrainColumn(int x, int z, int targetY, BlockState surface, BlockState foundation) {
            this(x, z, targetY, surface, foundation, 0);
        }
    }

    /** Cleanup-only column; unlike TerrainColumn it never grades or claims the ground. */
    public record VegetationColumn(int x, int z, int baseY) { }

    /** Decoration whose vertical datum is the final locally graded surface. */
    public record SurfaceDecoration(int x, int z, int offsetY, BlockState state) { }

    public record RailColumn(BlockPos rail, BlockState railState, BlockState support) { }
}
