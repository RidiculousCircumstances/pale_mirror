package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3GroundItemMerge;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3GroundItemPosition;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3GroundPlayerPickup;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3GroundItemDestruction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
abstract class FrontierV3GroundItemMergeMixin {
    @Redirect(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V"))
    private static void frontierV3$consumedMergeSource(ItemEntity source) {
        FrontierV3GroundItemDestruction.discardConsumed(source);
    }
    @Redirect(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean frontierV3$playerPickup(Inventory inventory, ItemStack stack) {
        return FrontierV3GroundPlayerPickup.add((ItemEntity) (Object) this, inventory, stack);
    }
    @Inject(method = "tick", at = @At("RETURN"))
    private void frontierV3$position(CallbackInfo callback) { FrontierV3GroundItemPosition.observed((ItemEntity) (Object) this); }
    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void frontierV3$before(ItemEntity destination, ItemStack destinationStack, ItemEntity source, ItemStack sourceStack, CallbackInfo callback) {
        if (!FrontierV3GroundItemMerge.before(destination, destinationStack, source, sourceStack)) callback.cancel();
    }
    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private static void frontierV3$after(ItemEntity destination, ItemStack destinationStack, ItemEntity source, ItemStack sourceStack, CallbackInfo callback) {
        FrontierV3GroundItemMerge.after(destination);
    }
}
