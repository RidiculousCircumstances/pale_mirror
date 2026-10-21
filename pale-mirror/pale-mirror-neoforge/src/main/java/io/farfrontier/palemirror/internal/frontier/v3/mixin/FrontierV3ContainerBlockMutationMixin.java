package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ContainerBlockOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

/** Owns the outer palette write, not the later inventory-drop notification. */
@Mixin(LevelChunk.class)
abstract class FrontierV3ContainerBlockMutationMixin {
    @WrapMethod(method = "setBlockState")
    private BlockState frontierV3$replaceContainer(BlockPos position, BlockState replacement, boolean moving,
            Operation<BlockState> original) {
        return FrontierV3ContainerBlockOperation.replace((LevelChunk) (Object) this, position, replacement,
                () -> original.call(position, replacement, moving));
    }
}
