package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.*;

/** Transfers existing receipts across native block-entity removal. A serialized absence
 * alone grants nothing: the removed exact incarnation supplied these receipts beforehand. */
public final class FrontierV3RetiredBlockEntityReceipts {
    private static final FrontierV3NativeRetirementBag BAG = new FrontierV3NativeRetirementBag(
            "removed_block_entity_receipts", FrontierV3OperationReceipt.Medium.TERRAIN);
    private FrontierV3RetiredBlockEntityReceipts() { }
    public static void beforeRemoval(LevelChunk chunk, BlockPos position) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) return;
        var entity = chunk.getBlockEntities().get(position); if (entity == null) return;
        var receipts = FrontierV3NativeOperationMarkers.read(entity.getPersistentData());
        if (receipts.isEmpty()) return;
        var bag = FrontierV3OperationChunks.data(chunk);
        prune(level, bag);
        for (var receipt : receipts) {
            if (receipt.participant().medium() != FrontierV3OperationReceipt.Medium.TERRAIN
                    || !receipt.participant().identity().equals(Long.toString(position.asLong()))
                    || receipt.participant().incarnation() != entity.getPersistentData().getLong(FrontierV3NativeOperationMarkers.LIFETIME))
                throw new IllegalStateException("removed block entity carries foreign operation evidence");
        }
        BAG.add(bag, receipts); chunk.setUnsaved(true);
    }
    static void prune(ServerLevel level, CompoundTag bag) {
        BAG.prune(level, bag);
    }
    static List<FrontierV3OperationReceipt.NativeReceipt> read(ChunkPos chunk, CompoundTag bag) {
        var receipts = BAG.read(bag);
        for (var receipt : receipts) {
            if (!new ChunkPos(BlockPos.of(Long.parseLong(receipt.participant().identity()))).equals(chunk))
                throw new IllegalStateException("removed block-entity receipt belongs to another chunk");
        }
        return receipts;
    }
    static List<FrontierV3OperationReceipt.NativeReceipt> saved(ChunkPos chunk, CompoundTag bag, CompoundTag image) {
        var present = new HashSet<FrontierV3OperationReceipt.Participant>();
        var entities = image.getList("block_entities", Tag.TAG_COMPOUND);
        if (!image.contains("block_entities", Tag.TAG_LIST) || !entities.equals(image.get("block_entities")))
            return List.of(); // malformed/omitted list is NOT proof of deletion
        for (var value : entities) {
            var row = (CompoundTag) value;
            if (!row.contains("x", Tag.TAG_INT) || !row.contains("y", Tag.TAG_INT) || !row.contains("z", Tag.TAG_INT)) return List.of();
            var data = row.getCompound("NeoForgeData");
            present.add(new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.TERRAIN,
                    Long.toString(new BlockPos(row.getInt("x"), row.getInt("y"), row.getInt("z")).asLong()),
                    data.getLong(FrontierV3NativeOperationMarkers.LIFETIME)));
        }
        return read(chunk, bag).stream().filter(receipt -> !present.contains(receipt.participant())).toList();
    }
}
