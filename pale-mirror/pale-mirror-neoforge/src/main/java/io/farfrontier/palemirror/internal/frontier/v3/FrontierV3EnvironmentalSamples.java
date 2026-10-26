package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;

/** Explicit native random-sample admission; no player intent, timer or activity dispatch. */
public final class FrontierV3EnvironmentalSamples {
    private FrontierV3EnvironmentalSamples() { }
    public static boolean mutate(ServerLevel level, java.util.function.BooleanSupplier mutation) {
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        return !FrontierV3PhysicalWorld.isPhysical(level) || runtime == null
                ? mutation.getAsBoolean() : runtime.runEnvironmentalMutation(mutation);
    }
    public static void run(ServerLevel level, Runnable sample) {
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null) sample.run();
        else runtime.runEnvironmentalSample(sample);
    }
}
