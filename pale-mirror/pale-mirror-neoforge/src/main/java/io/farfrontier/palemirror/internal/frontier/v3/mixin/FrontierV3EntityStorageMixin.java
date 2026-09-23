package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3EntitySaveBoundary;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.ChunkEntities;

import java.util.concurrent.CompletableFuture;
import java.util.Optional;

/** Observes vanilla's actual write future, including the empty-chunk deletion branch. */
@Mixin(EntityStorage.class)
abstract class FrontierV3EntityStorageMixin implements FrontierV3EntitySaveBoundary {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private SimpleRegionStorage simpleRegionStorage;
    @Shadow @Final private LongSet emptyChunks;

    @Inject(method = "loadEntities", at = @At("HEAD"), require = 1)
    private void frontierV3$observeCachedRead(ChunkPos chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkEntities<Entity>>> callback) {
        if (emptyChunks.contains(chunk.toLong())) {
            FrontierV3ServerLifecycle.observeEntityChunkRead(level, chunk, CompletableFuture.completedFuture(Optional.empty()));
        }
    }

    @Inject(method = "storeEntities", at = @At("HEAD"), require = 1)
    private void frontierV3$observeCachedEmptyStore(ChunkEntities<Entity> entities, CallbackInfo callback) {
        if (entities.isEmpty() && emptyChunks.contains(entities.getPos().toLong())) {
            // Vanilla elides this write. Observe its cached empty state as a read,
            // never as a new successful write that could waive an earlier IO failure.
            FrontierV3ServerLifecycle.observeEntityChunkRead(level, entities.getPos(), CompletableFuture.completedFuture(Optional.empty()));
        }
    }

    @Override public void frontierV3$completeSavePass(boolean complete) {
        FrontierV3ServerLifecycle.completeEntitySavePass(level, complete, () -> simpleRegionStorage.synchronize(true));
    }

    @Redirect(method = "storeEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/storage/SimpleRegionStorage;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"), require = 2)
    private CompletableFuture<Void> frontierV3$observeWrite(SimpleRegionStorage storage, ChunkPos chunk, CompoundTag data) {
        var written = FrontierV3ServerLifecycle.writeEntityChunkWithFootprint(level, chunk, data, () -> storage.write(chunk, data));
        written.whenComplete((ignored, failure) -> {
            if (failure != null) level.getServer().execute(() -> emptyChunks.remove(chunk.toLong()));
        });
        FrontierV3ServerLifecycle.observeEntityChunkWrite(level, chunk, data, written);
        return written;
    }

    @Redirect(method = "loadEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/storage/SimpleRegionStorage;read(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;"), require = 1)
    private CompletableFuture<Optional<CompoundTag>> frontierV3$observeRead(SimpleRegionStorage storage, ChunkPos chunk) {
        return FrontierV3ServerLifecycle.observeEntityChunkRead(level, chunk, storage.read(chunk));
    }
}
