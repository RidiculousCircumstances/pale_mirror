package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3OperationChunks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
abstract class FrontierV3OperationChunkSerializerMixin {
    @Inject(method = "read", at = @At("RETURN"), require = 1)
    private static void paleMirror$read(ServerLevel level, PoiManager poi, RegionStorageInfo storage,
            ChunkPos position, CompoundTag data, CallbackInfoReturnable<ProtoChunk> callback) {
        FrontierV3OperationChunks.read(callback.getReturnValue(), data);
        FrontierV3OperationChunks.requestReceiptResave(callback.getReturnValue(), data);
    }
    @Inject(method = "write", at = @At("RETURN"), require = 1)
    private static void paleMirror$write(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> callback) {
        FrontierV3OperationChunks.write(chunk, callback.getReturnValue());
    }
}
