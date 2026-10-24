package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3UnloadingTerrainAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Ordered, flushed no-load terrain reads; no cached image grants authority across a native write. */
public final class FrontierV3StoredTerrainInspection {
    private static final Map<ServerLevel, Long> WRITES = new WeakHashMap<>();
    private FrontierV3StoredTerrainInspection() { }
    static long epoch(ServerLevel level) { return WRITES.getOrDefault(level, 0L); }
    public static void wrote(ServerLevel level) { WRITES.put(level, Math.incrementExact(epoch(level))); }
    static boolean unloaded(ServerLevel level, ChunkPos column) {
        var ownership = (FrontierV3UnloadingTerrainAccessor) level.getChunkSource().chunkMap;
        return !ownership.frontierV3$getUpdatingChunks().containsKey(column.toLong())
                && !level.getChunkSource().hasChunk(column.x, column.z)
                && !((FrontierV3UnloadingTerrainAccessor) level.getChunkSource().chunkMap)
                    .frontierV3$getPendingUnloads().containsKey(column.toLong());
    }
    static CompletableFuture<Snapshot> inspect(ServerLevel level, ChunkPos column) {
        if (!unloaded(level, column)) throw new IllegalArgumentException("stored terrain inspection requires an unloaded column");
        var storage = level.getChunkSource().chunkMap;
        long before = epoch(level);
        return ((FrontierV3TerrainStorageAccess) storage).frontierV3$synchronizeTerrain()
                .thenCompose(ignored -> storage.read(column))
                .thenApplyAsync(raw -> new Snapshot(column, raw.map(CompoundTag::copy), before), level.getServer());
    }
    record Snapshot(ChunkPos column, Optional<CompoundTag> data, long writeEpoch) {
        Snapshot {
            data = data.map(CompoundTag::copy);
            data.ifPresent(tag -> {
                if (!tag.contains("xPos", Tag.TAG_INT) || !tag.contains("zPos", Tag.TAG_INT)
                        || tag.getInt("xPos") != column.x || tag.getInt("zPos") != column.z)
                    throw new IllegalArgumentException("stored terrain has a foreign column address");
            });
        }
        boolean current(ServerLevel level) { return epoch(level) == writeEpoch && unloaded(level, column); }
    }
}
