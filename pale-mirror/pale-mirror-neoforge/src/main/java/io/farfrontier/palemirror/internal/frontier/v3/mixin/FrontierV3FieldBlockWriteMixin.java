package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3BlockMutationBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Durable geometry fence and exact owner witnesses before vanilla's ordinary block mutation. */
@Mixin(Level.class)
abstract class FrontierV3FieldBlockWriteMixin {
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"), require = 1, cancellable = true)
    private void frontierV3$beforeDestructionDrops(BlockPos position, boolean drop,
            net.minecraft.world.entity.Entity source, int recursionLeft, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerLevel level) {
            var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk != null && !chunk.getBlockState(position).isAir()
                    && !FrontierV3BlockMutationBridge.prepareDestruction(level, position,
                            chunk.getFluidState(position).createLegacyBlock())) {
                var current = chunk.getBlockState(position);
                level.sendBlockUpdated(position, current, current, 3);
                callback.setReturnValue(false);
            }
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), require = 1, cancellable = true)
    private void frontierV3$beforeFieldBlockWrite(BlockPos position, BlockState replacement, int flags,
                                                   int recursionLeft, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerLevel level
                && !FrontierV3BlockMutationBridge.observe(level, position, replacement, false)) {
            var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk != null) {
                var current = chunk.getBlockState(position);
                level.sendBlockUpdated(position, current, current, 3);
            }
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"), require = 1)
    private void frontierV3$afterFieldBlockWrite(BlockPos position, BlockState replacement, int flags,
                                                int recursionLeft, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && (Object) this instanceof ServerLevel level)
            FrontierV3BlockMutationBridge.observe(level, position, replacement, true);
    }
}
