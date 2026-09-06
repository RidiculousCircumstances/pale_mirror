package io.farfrontier.palemirror.internal.integration.spore.mixin;

import io.farfrontier.palemirror.internal.integration.spore.SporeRuntimeFirewall;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses upstream death conversion, rewards and hivemind progression. */
@Mixin(targets = "com.Harbinger.Spore.Sevents.Infection")
abstract class SporeInfectionRuntimeMixin {
    @Inject(method = "onEntityDeath", at = @At("HEAD"), cancellable = true, require = 0)
    private static void paleMirror$disableNativeInfection(CallbackInfo callback) {
        SporeRuntimeFirewall.observeGlobalHook();
        if (SporeRuntimeFirewall.enabled()) callback.cancel();
    }
}
