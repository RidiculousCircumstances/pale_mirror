package com.vibecraft.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Reject unknown client chunks instead of presenting their placeholder air as geometry. */
final class RegionAccess {
    private RegionAccess() {}
    static void requireLoaded(ClientLevel level, int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ, long maximumBlocks) {
        long x = (long) maxX - minX + 1;
        long y = (long) maxY - minY + 1;
        long z = (long) maxZ - minZ + 1;
        if (x < 1 || y < 1 || z < 1 || x > maximumBlocks || y > maximumBlocks
                || z > maximumBlocks || x > maximumBlocks / y || x * y > maximumBlocks / z) {
            throw new IllegalArgumentException("Region too large or invalid; maximum " + maximumBlocks + " blocks");
        }
        if (minX < -30_000_000 || maxX >= 30_000_000 || minZ < -30_000_000 || maxZ >= 30_000_000
                || minY < level.getMinBuildHeight() || maxY >= level.getMaxBuildHeight()) {
            throw new IllegalArgumentException("Region is outside world bounds");
        }
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) == null) {
                    throw new IllegalArgumentException("Client chunk not loaded: " + cx + "," + cz);
                }
            }
        }
    }
}
