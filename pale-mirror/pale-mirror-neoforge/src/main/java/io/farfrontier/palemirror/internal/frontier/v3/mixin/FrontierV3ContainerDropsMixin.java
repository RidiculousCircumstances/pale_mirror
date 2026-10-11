package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ContainerDrops;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Capture the actual vanilla slot-to-entity call without replacing its drop/randomization rules. */
@Mixin(Containers.class)
abstract class FrontierV3ContainerDropsMixin {
    @Redirect(method = "dropContents(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/Container;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"))
    private static void frontierV3$drop(Level target, double x, double y, double z, ItemStack stack,
            Level level, double originX, double originY, double originZ, Container container) {
        FrontierV3ContainerDrops.drop(target, x, y, z, stack, container);
    }
    @Redirect(method = "dropItemStack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean frontierV3$spawn(Level level, Entity entity) { return FrontierV3ContainerDrops.addFreshEntity(level, entity); }
}
