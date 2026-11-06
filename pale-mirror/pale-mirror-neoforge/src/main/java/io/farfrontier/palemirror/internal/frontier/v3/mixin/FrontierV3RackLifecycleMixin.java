package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ItemHandlerStorage;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native rack appearance cannot outlive the block being removed or replaced. */
@Pseudo
@Mixin(targets = "com.minecolonies.core.tileentities.TileEntityRack", remap = false)
abstract class FrontierV3RackLifecycleMixin {
    @Inject(method = "updateItemStorage", at = @At("HEAD"), cancellable = true)
    private void frontierV3$requireCurrentProvider(CallbackInfo callback) {
        if (!FrontierV3ItemHandlerStorage.providerStillPresent((BlockEntity) (Object) this)) callback.cancel();
    }
}
