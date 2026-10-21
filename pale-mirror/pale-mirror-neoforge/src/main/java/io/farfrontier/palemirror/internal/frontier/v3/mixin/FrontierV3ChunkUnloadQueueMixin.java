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
    @Unique private boolean frontierV3$unloadPassStarted;

    @Inject(method = "processUnloads", at = @At("HEAD"), require = 1)
    private void frontierV3$beginUnloadPass(BooleanSupplier budget, CallbackInfo callback) {
        frontierV3$boundedShutdown = level.getServer().isStopped()
                && FrontierV3ServerLifecycle.ownsPhysicalWorld(level.getServer());
        frontierV3$unloadPassStarted = false;
    }

    @Redirect(method = "processUnloads", at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;"), require = 1)
    private Object frontierV3$pollUnloadCallback(Queue<Runnable> queue) {
        if (!frontierV3$boundedShutdown) return queue.poll();
        if (!frontierV3$unloadPassStarted) {
            // Snapshot the real queue at the first drain, after new unloads have
            // been scheduled. C2ME independently replaces vanilla's size query
            // with MAX_VALUE; neither intercept nor consume that budget sentinel.
            frontierV3$unloadPass.begin(queue.size());
            frontierV3$unloadPassStarted = true;
        }
        return frontierV3$unloadPass.poll(queue);
    }
}
