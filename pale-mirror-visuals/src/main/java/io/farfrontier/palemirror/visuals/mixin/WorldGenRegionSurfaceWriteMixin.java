package io.farfrontier.palemirror.visuals.mixin;

import io.farfrontier.palemirror.visuals.genesis.WorldgenFeatureContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
abstract class WorldGenRegionSurfaceWriteMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void paleMirror$rejectForeignSurfaceWrite(BlockPos position, BlockState state, int flags,
                                                       int recursionLeft, CallbackInfoReturnable<Boolean> result) {
        if (!WorldgenFeatureContext.allowWrite(position, state)) result.setReturnValue(false);
    }
}
