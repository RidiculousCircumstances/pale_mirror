package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3GroundInventoryPickup;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3GroundItemDestruction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(HopperBlockEntity.class)
abstract class FrontierV3GroundInventoryPickupMixin {
    @WrapMethod(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z")
    private static boolean frontierV3$pickup(Container destination, ItemEntity source, Operation<Boolean> original) {
        return FrontierV3GroundInventoryPickup.add(destination, source, () -> original.call(destination, source));
    }
    @Redirect(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V"))
    private static void frontierV3$consumed(ItemEntity source) {
        FrontierV3GroundItemDestruction.discardConsumed(source);
    }
}
