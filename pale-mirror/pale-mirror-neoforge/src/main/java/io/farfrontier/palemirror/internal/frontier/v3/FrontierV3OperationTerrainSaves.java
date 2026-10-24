package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Native region-write adapter. Receipts and block-entity contents come from the SAME
 * serialized chunk; a save event or a successful mutation is never a disk acknowledgement. */
public final class FrontierV3OperationTerrainSaves {
    private FrontierV3OperationTerrainSaves() { }
    public static void observe(ServerLevel level, ChunkPos chunk, CompoundTag data, CompletableFuture<Void> written) {
        FrontierV3StoredTerrainInspection.wrote(level);
        if (!FrontierV3ServerLifecycle.acceptsNativeOperationSaves(level)) return;
        FrontierV3NativeOperationSaves.observe(level, FrontierV3OperationReceipt.Medium.TERRAIN,
                chunk.toLong(), receipts(chunk, data), written);
    }
    public static void complete(ServerLevel level, Supplier<CompletableFuture<Void>> synchronize) {
        if (FrontierV3ServerLifecycle.acceptsNativeOperationSaves(level))
            FrontierV3NativeOperationSaves.complete(level, FrontierV3OperationReceipt.Medium.TERRAIN, true, synchronize);
    }
    static List<FrontierV3OperationReceipt.NativeReceipt> receipts(ChunkPos chunk, CompoundTag data) {
        if (data == null) return List.of();
        if (!data.contains("xPos", Tag.TAG_INT) || !data.contains("zPos", Tag.TAG_INT)
                || data.getInt("xPos") != chunk.x || data.getInt("zPos") != chunk.z) return List.of();
        var found = new ArrayList<>(FrontierV3OperationChunks.receipts(chunk, data));
        var rows = data.getList("block_entities", Tag.TAG_COMPOUND);
        if (!data.contains("block_entities", Tag.TAG_LIST) || !rows.equals(data.get("block_entities"))) return List.copyOf(found);
        for (int i = 0; i < rows.size(); i++) {
            var entity = rows.getCompound(i); var persistent = entity.getCompound("NeoForgeData");
            for (var receipt : FrontierV3NativeOperationMarkers.read(persistent)) {
                var position = new BlockPos(entity.getInt("x"), entity.getInt("y"), entity.getInt("z"));
                var participant = receipt.participant();
                if (!entity.contains("x", Tag.TAG_INT) || !entity.contains("y", Tag.TAG_INT) || !entity.contains("z", Tag.TAG_INT)
                        || !new ChunkPos(position).equals(chunk) || participant.medium() != FrontierV3OperationReceipt.Medium.TERRAIN
                        || !participant.identity().equals(Long.toString(position.asLong()))
                        || !persistent.contains(FrontierV3NativeOperationMarkers.LIFETIME, Tag.TAG_LONG)
                        || participant.incarnation() != persistent.getLong(FrontierV3NativeOperationMarkers.LIFETIME))
                    throw new IllegalStateException("native operation marker has a foreign block-entity address/lifetime");
                found.add(receipt);
            }
        }
        return List.copyOf(found);
    }
}
