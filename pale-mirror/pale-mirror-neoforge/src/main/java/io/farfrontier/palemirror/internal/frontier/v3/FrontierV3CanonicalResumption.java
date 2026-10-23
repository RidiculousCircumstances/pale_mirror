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
    private FrontierV3CanonicalResumption() { }
    static void bind(MinecraftServer server, FrontierV3ServerRuntime<?, ?> runtime,
            BooleanSupplier admitted, Runnable checkFailure) {
        if (TURNS.containsKey(server)) throw new IllegalStateException("canonical resumption already bound");
        var turn = new FrontierV3ReadyTurn(task -> server.tell(new TickTask(server.getTickCount(), task)),
                () -> resume(server, runtime, admitted, checkFailure));
        TURNS.put(server, turn); runtime.onReady(turn::signal);
    }
    /** No native-tick reentry, invented time, physical replay or renewed allowance. */
    private static void resume(MinecraftServer server, FrontierV3ServerRuntime<?, ?> runtime,
            BooleanSupplier admitted, Runnable checkFailure) {
        if (!admitted.getAsBoolean() || FrontierV3HostTiming.inHostTick(server)
                || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE
                || !FrontierV3PedestrianPlanning.recovered(runtime)) return;
        try (var turn = FrontierV3HostTiming.beginReadyTurn(runtime.executionMetrics())) {
            runtime.runPersistenceTurn(() -> ImmutablePlanningScope.within(() -> {
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
