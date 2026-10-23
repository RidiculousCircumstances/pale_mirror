package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Same native block entity, not a logical depot or a nearby container inferred by location. */
final class FrontierV3OperationBlockEntityParticipant {
    private FrontierV3OperationBlockEntityParticipant() { }
    static FrontierV3MinecraftOperation.Participant current(BlockEntity entity) {
        if (entity.isRemoved() || !(entity.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || !FrontierV3LoadedWorld.ready(level, entity.getBlockPos()) || level.getBlockEntity(entity.getBlockPos()) != entity)
            throw new IllegalArgumentException("native operation requires the current naturally loaded block entity");
        var data = entity.getPersistentData();
        var identity = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.TERRAIN,
                Long.toString(entity.getBlockPos().asLong()), FrontierV3NativeOperationMarkers.lifetime(data));
        return new FrontierV3MinecraftOperation.Participant(identity, data, entity::setChanged);
    }
}
