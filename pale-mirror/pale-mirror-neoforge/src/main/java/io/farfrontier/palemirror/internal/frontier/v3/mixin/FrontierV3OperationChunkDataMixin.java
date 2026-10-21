package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3OperationChunkData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelChunk.class)
abstract class FrontierV3OperationChunkDataMixin implements FrontierV3OperationChunkData {
    @Unique private CompoundTag paleMirror$operationData;
    @Override public CompoundTag paleMirror$operationData() { return paleMirror$operationData; }
    @Override public void paleMirror$operationData(CompoundTag value) { paleMirror$operationData = value; }
    @org.spongepowered.asm.mixin.injection.Inject(method = "removeBlockEntity", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), require = 1)
    private void paleMirror$retainRemovedReceipts(net.minecraft.core.BlockPos position,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        io.farfrontier.palemirror.internal.frontier.v3.FrontierV3RetiredBlockEntityReceipts.beforeRemoval((LevelChunk) (Object) this, position);
    }
}
