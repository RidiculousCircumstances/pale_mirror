package io.farfrontier.palemirror.internal.integration.spore.mixin;

import io.farfrontier.palemirror.internal.integration.spore.SporeRuntimeFirewall;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents PM-owned infected forms from entering Spore's block-breaking aiStep path. */
@Mixin(targets = "com.Harbinger.Spore.Sentities.BaseEntities.Infected")
abstract class SporeInfectedSafetyMixin {
    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true, require = 0)
    private void paleMirror$disableOwnedNativeAi(CallbackInfo callback) {
        if (SporeRuntimeFirewall.suppressOwnedActorAi((Entity) (Object) this)) callback.cancel();
    }
}
