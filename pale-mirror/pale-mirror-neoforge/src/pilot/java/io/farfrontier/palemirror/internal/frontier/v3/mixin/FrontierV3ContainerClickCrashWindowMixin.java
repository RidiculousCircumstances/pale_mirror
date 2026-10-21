package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PilotCrashHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the ordinary completed menu callback before its canonical confirmation. */
@Mixin(targets = "io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ContainerClickExecutor")
abstract class FrontierV3ContainerClickCrashWindowMixin {
    @Inject(method = "after", at = @At("HEAD"))
    private static void afterNativeClick(ServerPlayer player, AbstractContainerMenu menu, @Coerce Object runtime, CallbackInfo ignored) {
        FrontierV3PilotCrashHooks.afterVisibleContainerClick(player, runtime);
    }
}
