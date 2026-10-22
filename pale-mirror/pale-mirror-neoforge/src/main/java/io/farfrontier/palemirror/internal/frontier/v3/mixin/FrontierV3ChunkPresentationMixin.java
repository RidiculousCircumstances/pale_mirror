package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ChunkSendQueue;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Keeps unready chunks in vanilla's pending queue; batch counts/acks include only actually sent chunks. */
@Mixin(PlayerChunkSender.class)
abstract class FrontierV3ChunkPresentationMixin {
    @Shadow @Final private LongSet pendingChunks;
    @Shadow private float batchQuota;
    @Unique private final FrontierV3ChunkSendQueue paleMirror$sendQueue = new FrontierV3ChunkSendQueue();
    @Unique private ServerLevel paleMirror$presentationLevel;
    @Inject(method = "markChunkPendingToSend", at = @At("TAIL"))
    private void paleMirror$queued(LevelChunk chunk, CallbackInfo ci) {
        paleMirror$sendQueue.add(chunk.getPos().toLong());
    }
    @Inject(method = "dropChunk", at = @At("HEAD"))
    private void paleMirror$dropped(ServerPlayer player, ChunkPos chunk, CallbackInfo ci) {
        paleMirror$sendQueue.remove(chunk.toLong());
    }
    @Inject(method = "sendNextChunks", at = @At("HEAD"))
    private void paleMirror$captureLevel(ServerPlayer player, CallbackInfo ci) {
        paleMirror$presentationLevel = player.serverLevel();
    }
    @Inject(method = "collectChunksToSend", at = @At("HEAD"), cancellable = true)
    private void paleMirror$filterReady(ChunkMap chunks, ChunkPos origin, CallbackInfoReturnable<List<LevelChunk>> ci) {
        if (paleMirror$presentationLevel == null
                || !FrontierV3ServerLifecycle.ownsPhysicalWorld(paleMirror$presentationLevel.getServer())
                || !io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PhysicalWorld.isPhysical(paleMirror$presentationLevel)) return;
        var ready = paleMirror$sendQueue.collect(origin, net.minecraft.util.Mth.floor(batchQuota),
                key -> pendingChunks.contains(key) ? chunks.getChunkToSend(key) : null,
                chunk -> FrontierV3ServerLifecycle.chunkPresentationReady(paleMirror$presentationLevel, chunk.getPos()));
        for (var chunk : ready) pendingChunks.remove(chunk.getPos().toLong());
        ci.setReturnValue(ready);
    }
    @Inject(method = "collectChunksToSend", at = @At("RETURN"))
    private void paleMirror$sent(ChunkMap chunks, ChunkPos origin, CallbackInfoReturnable<List<LevelChunk>> ci) {
        // Also clean the index when vanilla owns another dimension or runtime mode.
        for (var chunk : ci.getReturnValue()) paleMirror$sendQueue.remove(chunk.getPos().toLong());
    }
}
