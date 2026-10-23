package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PlayerTerrainReadiness;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A stationary network re-anchor must not synchronously demand an unfinished chunk. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class FrontierV3PlayerPositionResetMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;absMoveTo(DDDFF)V"), require = 1)
    private void frontierV3$resetPosition(ServerPlayer player, double x, double y, double z,
            float yaw, float pitch, Operation<Void> original) {
        if (!FrontierV3PlayerTerrainReadiness.resetUnchangedPosition(player, x, y, z, yaw, pitch))
            original.call(player, x, y, z, yaw, pitch);
    }
}
