package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact external field-loss witness before vanilla's ordinary block mutation. */
@Mixin(Level.class)
abstract class FrontierV3FieldBlockWriteMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), require = 1)
    private void frontierV3$beforeFieldBlockWrite(BlockPos position, BlockState replacement, int flags,
                                                   int recursionLeft, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerLevel level)
            FrontierV3ServerLifecycle.observeFieldBlockWrite(level, position, replacement, false);
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"), require = 1)
    private void frontierV3$afterFieldBlockWrite(BlockPos position, BlockState replacement, int flags,
                                                int recursionLeft, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && (Object) this instanceof ServerLevel level)
            FrontierV3ServerLifecycle.observeFieldBlockWrite(level, position, replacement, true);
    }
}
