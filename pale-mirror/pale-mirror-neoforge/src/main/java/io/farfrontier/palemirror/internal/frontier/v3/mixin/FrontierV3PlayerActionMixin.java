package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the authoritative server receipt before vanilla mutates the physical block. */
@Mixin(ServerPlayerGameMode.class)
abstract class FrontierV3PlayerActionMixin {
    @Shadow protected ServerPlayer player;
    @Shadow protected ServerLevel level;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
    private void frontierV3$admitManagedBreak(BlockPos position, ServerboundPlayerActionPacket.Action action,
                                               net.minecraft.core.Direction face, int maxBuildHeight, int sequence,
                                               CallbackInfo callback) {
        if (action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) return;
        // ServerPlayerGameMode runs only after Minecraft scheduled the received packet onto
        // the owning server thread, before CommonHooks or vanilla mutate the block.
        FrontierV3ServerLifecycle.PlayerBreakDisposition disposition = FrontierV3ServerLifecycle.observePlayerBreakPacket(
                level, position, player);
        PaleMirrorMod.LOGGER.info("PMV3_PLAYER_BREAK boundary=server-game-mode disposition={}", disposition);
        if (disposition == FrontierV3ServerLifecycle.PlayerBreakDisposition.REJECTED) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(level, position));
            callback.cancel();
        }
    }
}
