package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Managed-soil policy at the vanilla boundary; remaining mutations retain attribution. */
@Mixin(FarmBlock.class)
abstract class FrontierV3FarmlandObservationMixin {
    @Inject(method = "turnToDirt", at = @At("HEAD"), cancellable = true, require = 1)
    private static void frontierV3$protectManagedSoil(Entity entity, BlockState previous, Level level,
                                                      BlockPos position, CallbackInfo callback) {
        if (level instanceof ServerLevel serverLevel
                && FrontierV3ServerLifecycle.blocksNativeSoilReversion(serverLevel, position)) callback.cancel();
    }

    @Inject(method = "turnToDirt", at = @At("TAIL"), require = 1)
    private static void frontierV3$observeSoilReversion(Entity entity, BlockState previous, Level level,
                                                       BlockPos position, CallbackInfo callback) {
        if (level instanceof ServerLevel serverLevel) {
            FrontierV3ServerLifecycle.observeFarmlandReversion(serverLevel, position, previous, entity);
        }
    }
}
