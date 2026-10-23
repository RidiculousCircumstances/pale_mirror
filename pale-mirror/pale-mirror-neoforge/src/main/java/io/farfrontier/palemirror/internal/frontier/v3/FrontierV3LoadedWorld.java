package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** Native observation readiness, not demand or custody. A chunk ticket/holder is not
 * a completed chunk: hasChunkAt followed by Level.getBlockState can block loading.
 * Callers read the returned chunk in this owner-thread turn and defer on null.
 * No tickets, loading, default air or retained cross-tick chunk references. */
final class FrontierV3LoadedWorld {
    private FrontierV3LoadedWorld() { }
    static LevelChunk chunk(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
    }
    static boolean readyAtBlockColumn(ServerLevel level, int x, int z) {
        return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
    }
    static boolean ready(ServerLevel level, BlockPos position) { return chunk(level, position) != null; }
}
