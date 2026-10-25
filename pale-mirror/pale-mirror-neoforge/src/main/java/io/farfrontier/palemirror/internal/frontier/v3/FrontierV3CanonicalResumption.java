package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.ImmutablePlanningScope;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Uses idle owner-loop capacity after a calculation or WAL receipt becomes ready.
 * Lifecycle supplies admission; runtime owns clock, budget, order and publication. */
final class FrontierV3CanonicalResumption {
    private static final Map<MinecraftServer, FrontierV3ReadyTurn> TURNS = new IdentityHashMap<>();
    private static final java.util.Set<MinecraftServer> NATIVE_TICKS =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private FrontierV3CanonicalResumption() { }
    /** Execution scope is independent of optional telemetry and covers all native tick handlers. */
    static void duringNativeTick(MinecraftServer server, Runnable action) {
        if (!NATIVE_TICKS.add(server)) throw new IllegalStateException("nested native server tick");
        try { action.run(); }
        finally { NATIVE_TICKS.remove(server); }
    }
    static void bind(MinecraftServer server, FrontierV3ServerRuntime<?, ?> runtime,
            BooleanSupplier admitted, Runnable physicalContinuation, Runnable checkFailure) {
        if (TURNS.containsKey(server)) throw new IllegalStateException("canonical resumption already bound");
        var turn = new FrontierV3ReadyTurn(task -> server.tell(new TickTask(server.getTickCount(), task)),
                () -> resume(server, runtime, admitted, physicalContinuation, checkFailure));
        TURNS.put(server, turn); runtime.onReady(turn::signal);
    }
    /** No native-tick reentry, invented time, physical replay or renewed allowance. */
    private static void resume(MinecraftServer server, FrontierV3ServerRuntime<?, ?> runtime,
            BooleanSupplier admitted, Runnable physicalContinuation, Runnable checkFailure) {
        if (!admitted.getAsBoolean() || NATIVE_TICKS.contains(server)
                || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE
                || !FrontierV3PedestrianPlanning.recovered(runtime)) return;
        try (var turn = FrontierV3HostTiming.beginReadyTurn(runtime.executionMetrics())) {
            runtime.runPersistenceTurn(() -> ImmutablePlanningScope.within(() -> {
                physicalContinuation.run();
                runtime.resumeOrdinaryTurn(); return null;
            }));
        } catch (RuntimeException error) {
            PaleMirrorMod.LOGGER.error("Frontier v3 ready continuation failed before quarantine", error);
            runtime.quarantine(error);
        }
        checkFailure.run();
    }
    static void forget(MinecraftServer server) {
        var turn = TURNS.remove(server);
        if (turn != null) turn.close();
    }
}
