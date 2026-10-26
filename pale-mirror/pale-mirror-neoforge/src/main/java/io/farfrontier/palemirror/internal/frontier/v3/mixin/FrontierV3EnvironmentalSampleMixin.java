package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3EnvironmentalSamples;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Random samples resample; scheduled callbacks retain their exact native queue records. */
@Mixin(ServerLevel.class)
abstract class FrontierV3EnvironmentalSampleMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V"), require = 2)
    private <T> void frontierV3$scheduledBatch(net.minecraft.world.ticks.LevelTicks<T> ticks, long now, int maximum,
            java.util.function.BiConsumer<BlockPos, T> action, Operation<Void> original) {
        var level = (ServerLevel) (Object) this;
        io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ScheduledNativeTicks.batch(level,
                () -> original.call(ticks, now, io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ScheduledNativeTicks.maximum(maximum), action));
    }
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"), require = 1)
    private void frontierV3$blockSample(BlockState state, ServerLevel level, BlockPos position,
            RandomSource random, Operation<Void> original) {
        FrontierV3EnvironmentalSamples.run(level, () -> original.call(state, level, position, random));
    }
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"), require = 1)
    private void frontierV3$fluidSample(FluidState state, net.minecraft.world.level.Level level, BlockPos position,
            RandomSource random, Operation<Void> original) {
        FrontierV3EnvironmentalSamples.run((ServerLevel) (Object) this, () -> original.call(state, level, position, random));
    }
}
