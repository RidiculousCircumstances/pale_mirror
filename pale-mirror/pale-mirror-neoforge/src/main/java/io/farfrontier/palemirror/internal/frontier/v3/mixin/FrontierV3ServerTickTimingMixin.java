package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3HostTiming;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.BooleanSupplier;

/** Observe the complete native tick, including every mod's Post handler, without its idle wait. */
@Mixin(MinecraftServer.class)
abstract class FrontierV3ServerTickTimingMixin {
    @WrapMethod(method = "saveEverything")
    private boolean frontierV3$measureSave(boolean quiet, boolean flush, boolean force, Operation<Boolean> original) {
        try (var span = FrontierV3HostTiming.beginNativeSave((MinecraftServer) (Object) this, "all", "world")) {
            return original.call(quiet, flush, force);
        }
    }
    @WrapMethod(method = "tickServer")
    private void frontierV3$measureTick(BooleanSupplier hasTime, Operation<Void> original) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        FrontierV3HostTiming.runHostTick(server, () -> original.call(hasTime));
    }
}
