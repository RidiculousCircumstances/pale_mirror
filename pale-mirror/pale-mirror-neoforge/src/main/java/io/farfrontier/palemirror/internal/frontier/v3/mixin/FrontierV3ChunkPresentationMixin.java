package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PresentationBatch;
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
    @Unique private ServerLevel paleMirror$presentationLevel;
    @Inject(method = "sendNextChunks", at = @At("HEAD"))
    private void paleMirror$captureLevel(ServerPlayer player, CallbackInfo ci) {
        paleMirror$presentationLevel = player.serverLevel();
    }
    @Inject(method = "collectChunksToSend", at = @At("RETURN"), cancellable = true)
    private void paleMirror$filterReady(ChunkMap chunks, ChunkPos origin, CallbackInfoReturnable<List<LevelChunk>> ci) {
        if (paleMirror$presentationLevel == null) return;
        var selected = ci.getReturnValue();
        var ready = FrontierV3PresentationBatch.select(selected,
                () -> pendingChunks.longStream().mapToObj(chunks::getChunkToSend).filter(java.util.Objects::nonNull)
                        .sorted(java.util.Comparator.comparingInt(chunk -> origin.distanceSquared(chunk.getPos()))),
                chunk -> FrontierV3ServerLifecycle.chunkPresentationReady(paleMirror$presentationLevel, chunk.getPos()),
                chunk -> pendingChunks.add(chunk.getPos().toLong()),
                chunk -> pendingChunks.remove(chunk.getPos().toLong()));
        ci.setReturnValue(ready);
    }
}
