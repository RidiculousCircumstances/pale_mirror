package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import java.util.*;

/** Full chunks alone accept physical operations; worldgen/proto chunks cannot receive them.
 * The serializer bridge is strict: receipt failure cannot quietly omit a critical attachment. */
public final class FrontierV3OperationChunks {
    private static final String KEY = "pm_v3_block_operations";
    private FrontierV3OperationChunks() { }
    private static LevelChunk full(ChunkAccess chunk) {
        return chunk instanceof ImposterProtoChunk wrapper ? wrapper.getWrapped()
                : chunk instanceof LevelChunk level ? level : null;
    }
    public static void read(ProtoChunk chunk, CompoundTag serialized) {
        if (!serialized.contains(KEY)) return;
        var full = full(chunk);
        if (full == null || !serialized.contains(KEY, Tag.TAG_COMPOUND))
            throw new IllegalStateException("block operation receipts require a full native chunk");
        receipts(chunk.getPos(), serialized); // Validate exact addresses before publishing the bag.
        ((FrontierV3OperationChunkData) full).paleMirror$operationData(serialized.getCompound(KEY).copy());
    }
    /** A shutdown can save native data after the host stops accepting callbacks.
     * Request a normal future save, never promote a cached read to a durability receipt. */
    public static void requestReceiptResave(ProtoChunk chunk, CompoundTag serialized) {
        var full = full(chunk);
        if (full != null && !FrontierV3OperationTerrainSaves.receipts(chunk.getPos(), serialized).isEmpty()) full.setUnsaved(true);
    }
    public static void write(ChunkAccess chunk, CompoundTag serialized) {
        var full = full(chunk); if (full == null) return;
        var data = ((FrontierV3OperationChunkData) full).paleMirror$operationData();
        if (data == null) return;
        if (full.getLevel() instanceof ServerLevel level) FrontierV3RetiredBlockEntityReceipts.prune(level, data);
        serialized.put(KEY, data.copy());
        receipts(chunk.getPos(), serialized);
    }
    static FrontierV3MinecraftOperation.Participant current(ServerLevel level, BlockPos position) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null || level.isOutsideBuildHeight(position))
            throw new IllegalArgumentException("block operation requires a naturally loaded full chunk");
        var data = data(chunk);
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.CHUNK_BLOCKS,
                Long.toString(position.asLong()), FrontierV3NativeOperationMarkers.lifetime(data));
        return new FrontierV3MinecraftOperation.Participant(participant, data, () -> chunk.setUnsaved(true));
    }
    static CompoundTag data(LevelChunk chunk) {
        var owner = (FrontierV3OperationChunkData) chunk;
        var data = owner.paleMirror$operationData();
        if (data == null) { data = new CompoundTag(); owner.paleMirror$operationData(data); }
        return data;
    }
    static List<FrontierV3OperationReceipt.NativeReceipt> receipts(ChunkPos chunk, CompoundTag serialized) {
        if (!serialized.contains(KEY)) return List.of();
        if (!serialized.contains(KEY, Tag.TAG_COMPOUND)) throw new IllegalStateException("invalid block operation receipt bag");
        var data = serialized.getCompound(KEY);
        var receipts = FrontierV3NativeOperationMarkers.read(data);
        for (var receipt : receipts) {
            var participant = receipt.participant();
            if (participant.medium() != FrontierV3OperationReceipt.Medium.CHUNK_BLOCKS
                    || !new ChunkPos(BlockPos.of(Long.parseLong(participant.identity()))).equals(chunk)
                    || !data.contains(FrontierV3NativeOperationMarkers.LIFETIME, Tag.TAG_LONG)
                    || participant.incarnation() != data.getLong(FrontierV3NativeOperationMarkers.LIFETIME))
                throw new IllegalStateException("block operation receipt has a foreign chunk/lifetime");
        }
        FrontierV3RetiredBlockEntityReceipts.read(chunk, data); // validate retained obligations even when the old object remains
        var complete = new ArrayList<>(receipts);
        complete.addAll(FrontierV3RetiredBlockEntityReceipts.saved(chunk, data, serialized));
        return List.copyOf(complete);
    }
}
