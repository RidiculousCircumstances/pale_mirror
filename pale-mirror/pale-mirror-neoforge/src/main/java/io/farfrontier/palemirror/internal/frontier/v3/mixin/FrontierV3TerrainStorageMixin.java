package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

/** Observe exactly the future returned by vanilla, not merely invocation of a save event. */
@Mixin(ChunkStorage.class)
abstract class FrontierV3TerrainStorageMixin implements FrontierV3TerrainStorageAccess {
    @Shadow @Final private IOWorker worker;
    @Override public CompletableFuture<Void> frontierV3$synchronizeTerrain() { return worker.synchronize(true); }
    @Inject(method = "write", at = @At("RETURN"), require = 1)
    private void frontierV3$observeTerrainWrite(ChunkPos chunk, CompoundTag data, CallbackInfoReturnable<CompletableFuture<Void>> callback) {
        if ((Object) this instanceof FrontierV3TerrainStorageOwner owner)
            FrontierV3OperationTerrainSaves.observe(owner.frontierV3$terrainLevel(), chunk, data, callback.getReturnValue());
    }
}
