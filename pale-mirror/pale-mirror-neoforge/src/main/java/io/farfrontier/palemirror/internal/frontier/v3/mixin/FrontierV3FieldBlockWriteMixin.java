package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3BlockMutationBridge;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3EnvironmentalSamples;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Durable geometry fence and exact witnesses for one complete native mutation. */
@Mixin(Level.class)
abstract class FrontierV3FieldBlockWriteMixin {
    @WrapMethod(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z")
    private boolean frontierV3$beforeDestructionDrops(BlockPos position, boolean drop,
            net.minecraft.world.entity.Entity source, int recursionLeft, Operation<Boolean> original) {
        if (!((Object) this instanceof ServerLevel level)) return original.call(position, drop, source, recursionLeft);
        return FrontierV3EnvironmentalSamples.mutate(level, () -> {
            var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk != null && !chunk.getBlockState(position).isAir()
                    && !FrontierV3BlockMutationBridge.prepareDestruction(level, position,
                            chunk.getFluidState(position).createLegacyBlock())) {
                var current = chunk.getBlockState(position);
                level.sendBlockUpdated(position, current, current, 3);
                return false;
            }
            return original.call(position, drop, source, recursionLeft);
        });
    }

    @WrapMethod(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean frontierV3$fieldBlockWrite(BlockPos position, BlockState replacement, int flags,
            int recursionLeft, Operation<Boolean> original) {
        if (!((Object) this instanceof ServerLevel level)) return original.call(position, replacement, flags, recursionLeft);
        return FrontierV3EnvironmentalSamples.mutate(level, () -> {
            if (!FrontierV3BlockMutationBridge.observe(level, position, replacement, false)) {
                var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
                if (chunk != null) {
                    var current = chunk.getBlockState(position);
                    level.sendBlockUpdated(position, current, current, 3);
                }
                return false;
            }
            boolean changed = original.call(position, replacement, flags, recursionLeft);
            if (changed) FrontierV3BlockMutationBridge.observe(level, position, replacement, true);
            return changed;
        });
    }
}
