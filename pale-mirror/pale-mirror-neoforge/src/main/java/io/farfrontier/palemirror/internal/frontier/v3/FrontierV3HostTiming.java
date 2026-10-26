package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics;
import net.minecraft.server.MinecraftServer;
import java.util.IdentityHashMap;
import java.util.Map;

/** Observational host spans: no execution admission, domain clocks or persistence authority. */
public final class FrontierV3HostTiming {
    private static final Map<MinecraftServer, FrontierExecutionMetrics.Span> TICKS = new IdentityHashMap<>();
    private FrontierV3HostTiming() { }
    public static void runHostTick(MinecraftServer server, Runnable action) {
        FrontierV3CanonicalResumption.duringNativeTick(server, () -> {
            beginHostTick(server);
            try { action.run(); }
            finally { endHostTick(server); }
        });
    }
    /** Complete native tick including all Post handlers; excludes the idle tick wait. */
    private static void beginHostTick(MinecraftServer server) {
        var runtime = FrontierV3ServerLifecycle.runtimeFor(server);
        if (runtime == null) return;
        endHostTick(server);
        runtime.beginEnvironmentalTurn((long) (io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_BUDGET_MILLIS.get() * 1_000_000D));
        TICKS.put(server, FrontierExecutionMetrics.safelyBegin(runtime.executionMetrics(),
                FrontierExecutionMetrics.Stage.HOST_TICK, "minecraft-complete-tick", "world"));
    }
    public static void endHostTick(MinecraftServer server) {
        var span = TICKS.remove(server);
        if (span != null) span.close();
    }
    static FrontierExecutionMetrics.Span beginPmTurn(FrontierExecutionMetrics metrics) {
        return FrontierExecutionMetrics.safelyBegin(metrics, FrontierExecutionMetrics.Stage.HOST_TURN,
                "pm-turn-including-durability", "world");
    }
    static FrontierExecutionMetrics.Span beginReadyTurn(FrontierExecutionMetrics metrics) {
        return FrontierExecutionMetrics.safelyBegin(metrics, FrontierExecutionMetrics.Stage.HOST_TURN,
                "pm-ready-continuation", "world");
    }
}
