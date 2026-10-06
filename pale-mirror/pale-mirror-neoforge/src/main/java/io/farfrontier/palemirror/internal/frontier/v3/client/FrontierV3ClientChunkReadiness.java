package io.farfrontier.palemirror.internal.frontier.v3.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Read actual received terrain, never ClientLevel.hasChunk's unconditional true. */
final class FrontierV3ClientChunkReadiness {
    private FrontierV3ClientChunkReadiness() { }

    static boolean received(ClientLevel level, BlockPos position) {
        return level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4);
    }
}
