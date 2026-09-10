package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;

/** The one real owner composition for structural first-observation and deferred aftermath. */
final class FrontierV3AftermathOwnerComposition {
    private FrontierV3AftermathOwnerComposition() { }

    static void projection(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierV3GrayboxExecutor.tick(level, runtime);
    }

    static void aftermath(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierV3DeferredAftermathExecutor.tick(level, runtime);
    }

    static void tick(FrontierV3AftermathPhysicalWorld world, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierV3GrayboxExecutor.tick(world, runtime);
        FrontierV3DeferredAftermathExecutor.tick(world, runtime);
    }
}
