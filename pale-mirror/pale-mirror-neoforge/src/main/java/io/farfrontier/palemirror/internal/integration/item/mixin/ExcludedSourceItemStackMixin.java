package io.farfrontier.palemirror.internal.integration.item.mixin;

import io.farfrontier.palemirror.internal.adapter.SourceItemFirewall;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Last-line firewall for legacy stacks: events cover normal interactions, but
 * source item inventory ticks and melee callbacks can otherwise bypass them.
 */
@Mixin(ItemStack.class)
abstract class ExcludedSourceItemStackMixin {
    @Inject(method = "inventoryTick", at = @At("HEAD"), cancellable = true)
    private void paleMirror$blockExcludedInventoryTick(Level level, Entity entity, int slot, boolean selected,
                                                       CallbackInfo callback) {
        if (!level.isClientSide() && SourceItemFirewall.blocks((ItemStack) (Object) this)) callback.cancel();
    }

    @Inject(method = "hurtEnemy", at = @At("HEAD"), cancellable = true)
    private void paleMirror$blockExcludedMelee(LivingEntity target, Player player, CallbackInfoReturnable<Boolean> callback) {
        if (!player.level().isClientSide() && SourceItemFirewall.blocks((ItemStack) (Object) this)) {
            callback.setReturnValue(false);
        }
    }
}
