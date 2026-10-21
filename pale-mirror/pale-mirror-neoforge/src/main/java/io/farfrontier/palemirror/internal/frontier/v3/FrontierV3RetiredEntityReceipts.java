package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import java.util.*;

/** Death/discard preserves prior operation evidence in the native entity column.
 * Unloading is not destruction and keeps evidence on the serialized entity itself. */
public final class FrontierV3RetiredEntityReceipts {
    static final FrontierV3NativeRetirementBag BAG = new FrontierV3NativeRetirementBag(
            "removed_entity_receipts", FrontierV3OperationReceipt.Medium.ENTITIES);
    private FrontierV3RetiredEntityReceipts() { }
    public static void beforeRemoval(Entity entity, Entity.RemovalReason reason) {
        if (!reason.shouldDestroy() || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) return;
        var receipts = FrontierV3NativeOperationMarkers.read(entity.getPersistentData());
        if (receipts.isEmpty()) return;
        for (var receipt : receipts) if (receipt.participant().medium() != FrontierV3OperationReceipt.Medium.ENTITIES
                || !receipt.participant().identity().equals(entity.getUUID().toString())
                || receipt.participant().incarnation() != entity.getPersistentData().getLong(FrontierV3NativeOperationMarkers.LIFETIME))
            throw new IllegalStateException("removed entity carries foreign operation evidence");
        var bag = FrontierV3OperationEntityColumns.forRemoval(level, entity.chunkPosition());
        BAG.prune(level, bag); BAG.add(bag, receipts);
    }
    static List<FrontierV3OperationReceipt.NativeReceipt> read(CompoundTag bag) {
        var receipts = BAG.read(bag);
        receipts.forEach(receipt -> UUID.fromString(receipt.participant().identity()));
        return receipts;
    }
    static List<FrontierV3OperationReceipt.NativeReceipt> saved(CompoundTag bag, CompoundTag image) {
        var entities = FrontierV3StoredEntityInventory.serializedEntities(image).orElse(null);
        if (entities == null) return List.of();
        return read(bag).stream().filter(receipt -> {
            var entity = entities.get(UUID.fromString(receipt.participant().identity()));
            return entity == null || entity.getCompound("NeoForgeData").getLong(FrontierV3NativeOperationMarkers.LIFETIME)
                    != receipt.participant().incarnation();
        }).toList();
    }
}
