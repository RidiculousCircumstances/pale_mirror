package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Entity-format adapter on the existing native storage bridge; no second save loop. */
final class FrontierV3OperationEntitySaves {
    private FrontierV3OperationEntitySaves() { }
    static void observe(ServerLevel level, ChunkPos chunk, CompoundTag data, CompletableFuture<Void> written) {
        if (!FrontierV3ServerLifecycle.acceptsNativeOperationSaves(level)) return;
        FrontierV3NativeOperationSaves.observe(level, FrontierV3OperationReceipt.Medium.ENTITIES,
                chunk.toLong(), receipts(chunk, data), written);
    }
    static void complete(ServerLevel level, boolean complete, Supplier<CompletableFuture<Void>> synchronize) {
        if (!FrontierV3ServerLifecycle.acceptsNativeOperationSaves(level)) return;
        FrontierV3NativeOperationSaves.complete(level, FrontierV3OperationReceipt.Medium.ENTITIES, complete, synchronize);
    }
    private static List<FrontierV3OperationReceipt.NativeReceipt> receipts(ChunkPos chunk, CompoundTag data) {
        if (!FrontierV3StoredEntityInventory.matchesStoredChunk(data, chunk)) return List.of();
        var entities = FrontierV3StoredEntityInventory.serializedEntities(data).orElse(null); if (entities == null) return List.of();
        var found = new ArrayList<>(FrontierV3OperationEntityColumns.receipts(chunk, data));
        for (var entity : entities.entrySet()) {
            var persistent = entity.getValue().getCompound("NeoForgeData");
            for (var receipt : FrontierV3NativeOperationMarkers.read(persistent)) {
                var participant = receipt.participant();
                if (participant.medium() != FrontierV3OperationReceipt.Medium.ENTITIES
                        || !participant.identity().equals(entity.getKey().toString())
                        || !persistent.contains(FrontierV3NativeOperationMarkers.LIFETIME, Tag.TAG_LONG)
                        || participant.incarnation() != persistent.getLong(FrontierV3NativeOperationMarkers.LIFETIME))
                    throw new IllegalStateException("native operation marker does not belong to its serialized entity incarnation");
                found.add(receipt);
            }
        }
        return List.copyOf(found);
    }
    /** Same adapter/parser and common batch used by the real write bridge. */
    static final class Batch extends FrontierV3NativeOperationSaves.Batch {
        void observe(ChunkPos chunk, CompoundTag data, CompletableFuture<Void> written) {
            observe(chunk.toLong(), receipts(chunk, data), written);
        }
    }
}
