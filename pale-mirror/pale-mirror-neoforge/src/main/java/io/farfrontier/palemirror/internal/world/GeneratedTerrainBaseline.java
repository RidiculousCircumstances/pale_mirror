package io.farfrontier.palemirror.internal.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only generated preimage; no chunk loads and no permission to overwrite player changes. */
public final class GeneratedTerrainBaseline {
    private GeneratedTerrainBaseline() { }
    public static BlockState block(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getGenerator().getBaseColumn(position.getX(), position.getZ(), level,
                level.getChunkSource().randomState()).getBlock(position.getY());
    }
    public static boolean matches(ServerLevel level, BlockPos position, BlockState observed) {
        return observed.equals(block(level, position));
    }
}
