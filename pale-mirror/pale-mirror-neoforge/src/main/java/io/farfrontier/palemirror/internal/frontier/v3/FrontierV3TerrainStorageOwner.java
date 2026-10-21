package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;

/** Supplied by the actual ChunkMap; world ownership is never inferred from a save path. */
public interface FrontierV3TerrainStorageOwner {
    ServerLevel frontierV3$terrainLevel();
}
