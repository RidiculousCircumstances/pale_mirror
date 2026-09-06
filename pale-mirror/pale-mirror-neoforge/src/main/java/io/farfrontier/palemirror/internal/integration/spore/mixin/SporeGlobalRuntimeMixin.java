package io.farfrontier.palemirror.internal.integration.spore.mixin;

import io.farfrontier.palemirror.internal.integration.spore.SporeRuntimeFirewall;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks the exact Spore 2.2.0j global event layer; PM schedules all threat work itself. */
@Mixin(targets = "com.Harbinger.Spore.Sevents.HandlerEvents")
abstract class SporeGlobalRuntimeMixin {
    @Inject(method = "onServerTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void paleMirror$disableGlobalServerTick(CallbackInfo callback) {
        SporeRuntimeFirewall.observeGlobalHook();
        if (SporeRuntimeFirewall.enabled()) callback.cancel();
    }

    @Inject(method = "onWorldLoad", at = @At("HEAD"), cancellable = true, require = 0)
    private static void paleMirror$disableWorldBootstrap(CallbackInfo callback) {
        SporeRuntimeFirewall.observeGlobalHook();
        if (SporeRuntimeFirewall.enabled()) callback.cancel();
    }

    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true, require = 0)
    private static void paleMirror$disableGlobalDeath(CallbackInfo callback) {
        SporeRuntimeFirewall.observeGlobalHook();
        if (SporeRuntimeFirewall.enabled()) callback.cancel();
    }

    @Inject(method = "onLivingSpawned", at = @At("HEAD"), cancellable = true, require = 0)
    private static void paleMirror$disableGlobalEntitySpawn(CallbackInfo callback) {
        SporeRuntimeFirewall.observeGlobalHook();
        if (SporeRuntimeFirewall.enabled()) callback.cancel();
    }
}
