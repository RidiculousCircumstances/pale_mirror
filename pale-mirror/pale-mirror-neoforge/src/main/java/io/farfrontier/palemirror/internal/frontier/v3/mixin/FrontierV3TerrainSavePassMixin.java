package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.*;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flush acknowledgements are batched after existing native save/unload passes. */
@Mixin(ChunkMap.class)
abstract class FrontierV3TerrainSavePassMixin implements FrontierV3TerrainStorageOwner {
    @Shadow @Final private ServerLevel level;
    @Override public ServerLevel frontierV3$terrainLevel() { return level; }
    @Inject(method = {"saveAllChunks", "processUnloads"}, at = @At("TAIL"), require = 2)
    private void frontierV3$completeTerrainPass(CallbackInfo callback) {
        FrontierV3OperationTerrainSaves.complete(level, ((FrontierV3TerrainStorageAccess) this)::frontierV3$synchronizeTerrain);
    }
}
