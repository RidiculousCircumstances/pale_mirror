package io.farfrontier.palemirror.visuals.genesis;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable work owned by exactly one naturally-generating chunk. */
public record CompiledChunkSlice(long chunkKey, String stamp, List<TerrainColumn> terrain,
                                 List<VegetationColumn> vegetation,
                                 List<AuthoredDecoration> decorations,
                                 List<RailColumn> rails, Map<BlockPos, CompiledBlock> blocks) {
    public CompiledChunkSlice {
        terrain = List.copyOf(terrain);
        vegetation = List.copyOf(vegetation);
        decorations = List.copyOf(decorations);
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

    /** Decoration pinned to the manifest's absolute first-air datum. */
    public record AuthoredDecoration(BlockPos position, BlockState state) { }

    public record RailColumn(BlockPos rail, BlockState railState, BlockState support,
                             boolean supportPier) { }

    /** Exact authored block plus optional local, already-relocated block-entity payload. */
    public record CompiledBlock(BlockState state, Optional<CompoundTag> blockEntityData) {
        public CompiledBlock {
            java.util.Objects.requireNonNull(state, "state");
            java.util.Objects.requireNonNull(blockEntityData, "blockEntityData");
            blockEntityData = blockEntityData.map(CompoundTag::copy);
            if (blockEntityData.isPresent() && !state.hasBlockEntity()) {
                throw new IllegalArgumentException("Block-entity payload requires a block-entity state: " + state);
            }
        }

        public CompiledBlock(BlockState state) { this(state, Optional.empty()); }

        @Override public Optional<CompoundTag> blockEntityData() {
            return blockEntityData.map(CompoundTag::copy);
        }
    }
}
