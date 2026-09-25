package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Read-only physical-provider capability; no entity or chunk admission. */
public interface FrontierV3StoredEntityInventoryAccess {
    CompletableFuture<Void> frontierV3$synchronizeStoredEntities();
    CompletableFuture<Optional<CompoundTag>> frontierV3$readStoredEntityChunkAfterSync(ChunkPos chunk);
    CompletableFuture<Optional<CompoundTag>> frontierV3$readStoredEntityChunk(ChunkPos chunk);
}
