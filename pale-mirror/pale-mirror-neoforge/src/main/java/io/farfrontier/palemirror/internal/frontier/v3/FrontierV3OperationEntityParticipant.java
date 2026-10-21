package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;

/** Exact native persistence participant, independent of the body's current job or inventory role. */
final class FrontierV3OperationEntityParticipant {
    private FrontierV3OperationEntityParticipant() { }
    static FrontierV3MinecraftOperation.Participant current(Entity entity, long physicalEpoch) {
        var data = entity.getPersistentData();
        if (!data.contains(FrontierV3ActorCarrierComposition.EPOCH_KEY, Tag.TAG_LONG)
                || data.getLong(FrontierV3ActorCarrierComposition.EPOCH_KEY) != physicalEpoch)
            throw new IllegalArgumentException("native operation has a foreign body incarnation");
        return nativeObject(entity);
    }
    static FrontierV3MinecraftOperation.Participant nativeObject(Entity entity) {
        if (entity.isRemoved() || !(entity.level() instanceof net.minecraft.server.level.ServerLevel level)
                || level.getEntity(entity.getUUID()) != entity)
            throw new IllegalArgumentException("native operation requires the exact loaded entity");
        var data = entity.getPersistentData();
        // Body authority can change on the SAME native object. Its operation receipts
        // must survive that handoff, while a newly constructed body gets a distinct lifetime.
        var identity = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITIES,
                entity.getUUID().toString(), FrontierV3NativeOperationMarkers.lifetime(data));
        // EntityStorage serializes the actual live body on its normal save/unload pass;
        // unlike block entities, entities expose no separate setChanged() inventory flag.
        return new FrontierV3MinecraftOperation.Participant(identity, data, () -> { });
    }
}
