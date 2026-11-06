package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.frontier.v3.model.StorageBlockKind;
import io.farfrontier.palemirror.internal.frontier.v3.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional integration: the real rack, with its own single-block inventory. */
@Pseudo
@Mixin(targets="com.minecolonies.api.tileentities.AbstractTileEntityRack", remap=false)
abstract class FrontierV3RackInventoryMixin implements FrontierV3ItemHandlerOwner {
    @Shadow protected net.neoforged.neoforge.items.ItemStackHandler inventory;
    @Override public IItemHandlerModifiable frontierV3$inventory() { return inventory; }
    @Override public StorageBlockKind frontierV3$storageKind() { return StorageBlockKind.RACK; }
    @Inject(method="getInventory", at=@At("RETURN"))
    private void frontierV3$declareInventory(CallbackInfoReturnable<IItemHandlerModifiable> result) {
        FrontierV3ItemHandlerStorage.register((BlockEntity)(Object)this,StorageBlockKind.RACK,result.getReturnValue());
    }
}
