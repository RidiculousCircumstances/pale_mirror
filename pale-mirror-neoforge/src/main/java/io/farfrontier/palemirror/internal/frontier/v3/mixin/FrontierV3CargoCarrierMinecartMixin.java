package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A leased v3 cargo cart is a road convoy in the graybox profile, not a Vanilla rail vehicle.
 *
 * <p>The scene executor owns its bounded, collision-checked movement. Vanilla minecart ticking
 * otherwise treats a route deck as empty space and makes the same exact cart fall through a
 * carpet/slab road. Damage, inventory interaction, explosions and entity collision are not
 * intercepted here; they remain ordinary Minecraft events and observation boundaries.</p>
 */
@Mixin(AbstractMinecart.class)
abstract class FrontierV3CargoCarrierMinecartMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void frontierV3$skipRailPhysicsForLeasedRoadCarrier(CallbackInfo callback) {
        AbstractMinecart cart = (AbstractMinecart) (Object) this;
        if (!FrontierV3ServerLifecycle.isLeasedRoadCargoCarrier(cart)) return;
        cart.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        callback.cancel();
    }
}
