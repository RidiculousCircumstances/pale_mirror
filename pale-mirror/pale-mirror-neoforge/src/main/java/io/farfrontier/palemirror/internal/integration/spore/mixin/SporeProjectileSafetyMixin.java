package io.farfrontier.palemirror.internal.integration.spore.mixin;

import io.farfrontier.palemirror.internal.integration.spore.SporeRuntimeFirewall;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact 2.2.0j visual carrier: PM replaces AcidBall's tick/hit/terrain path. */
@Mixin(targets = "com.Harbinger.Spore.Sentities.Projectile.AcidBall")
abstract class SporeProjectileSafetyMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void paleMirror$disableOwnedNativeProjectileTick(CallbackInfo callback) {
        if (SporeRuntimeFirewall.suppressOwnedProjectile((Entity) (Object) this)) callback.cancel();
    }
}
