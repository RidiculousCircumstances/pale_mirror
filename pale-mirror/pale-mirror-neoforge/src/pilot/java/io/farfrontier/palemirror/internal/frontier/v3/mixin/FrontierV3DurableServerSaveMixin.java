package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PilotCrashHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Test-pilot-only receipt after the normal Minecraft save-and-stop path has completed.
 * It is intentionally separate from the production lifecycle and from crash-window
 * probes: the external runner consumes this immutable receipt before it observes JVM
 * exit or reuses its port.
 */
@Mixin(targets = "net.minecraft.server.MinecraftServer")
abstract class FrontierV3DurableServerSaveMixin {
    @Inject(method = "stopServer", at = @At("TAIL"), require = 1)
    private void publishDurableServerSave(CallbackInfo ignored) {
        FrontierV3PilotCrashHooks.afterMinecraftServerDurablyStopped();
    }
}
