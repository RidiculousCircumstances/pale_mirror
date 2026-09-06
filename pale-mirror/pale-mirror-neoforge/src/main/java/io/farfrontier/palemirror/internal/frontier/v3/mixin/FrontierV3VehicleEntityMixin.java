package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Observes terminal vanilla vehicle damage before Minecraft discards/destroys its inventory.
 * The lifecycle narrows this general hook to an exact Frontier v3 chest-minecart carrier.
 */
@Mixin(VehicleEntity.class)
abstract class FrontierV3VehicleEntityMixin {
    @Inject(method = "hurt", at = @At("HEAD"))
    private void frontierV3$beforeTerminalDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        Entity entity = (Entity) (Object) this;
        if (!(entity.level() instanceof ServerLevel level) || entity.isRemoved() || entity.isInvulnerableTo(source)) return;
        boolean creative = source.getEntity() instanceof Player player && player.getAbilities().instabuild;
        if (creative || ((VehicleEntity) entity).getDamage() + amount * 10.0F > 40.0F) {
            FrontierV3ServerLifecycle.observeTerminalVehicleDamage(level, entity, source);
        }
    }
}
