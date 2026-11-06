package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ItemHandlerStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.minecolonies.api.util.InventoryUtils",remap=false)
abstract class FrontierV3RackDropsMixin {
    @Inject(method="dropItemHandler",at=@At("HEAD"),cancellable=true)
    private static void frontierV3$drop(IItemHandler inventory, Level level, int x,int y,int z,CallbackInfo callback) {
        if(FrontierV3ItemHandlerStorage.drop(inventory,level,x,y,z)) callback.cancel();
    }
}
