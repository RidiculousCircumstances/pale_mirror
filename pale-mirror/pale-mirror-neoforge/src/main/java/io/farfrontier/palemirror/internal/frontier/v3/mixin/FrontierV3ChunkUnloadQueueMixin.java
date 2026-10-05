package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3UnloadQueuePass;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;
import java.util.function.BooleanSupplier;

/**
 * Vanilla's unlimited shutdown budget can execute an immediate unload retry forever,
 * starving the outer server loop that completes generation/save dependencies.
 * Do not change readiness, callbacks, chunk custody or normal running-server budgets.
 */
@Mixin(ChunkMap.class)
abstract class FrontierV3ChunkUnloadQueueMixin {
    @Shadow @Final private ServerLevel level;
    @Unique private final FrontierV3UnloadQueuePass frontierV3$unloadPass = new FrontierV3UnloadQueuePass();
    @Unique private boolean frontierV3$boundedShutdown;

    @Inject(method = "processUnloads", at = @At("HEAD"), require = 1)
    private void frontierV3$beginUnloadPass(BooleanSupplier budget, CallbackInfo callback) {
        frontierV3$boundedShutdown = level.getServer().isStopped()
                && FrontierV3ServerLifecycle.ownsPhysicalWorld(level.getServer());
    }

    @Redirect(method = "processUnloads", at = @At(value = "INVOKE", target = "Ljava/util/Queue;size()I"), require = 1)
    private int frontierV3$captureQueuedCallbacks(Queue<Runnable> queue) {
        int queued = queue.size();
        if (frontierV3$boundedShutdown) frontierV3$unloadPass.begin(queued);
        return queued;
    }

    @Redirect(method = "processUnloads", at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;"), require = 1)
    private Object frontierV3$pollUnloadCallback(Queue<Runnable> queue) {
        return frontierV3$boundedShutdown ? frontierV3$unloadPass.poll(queue) : queue.poll();
    }
}
