package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded volatile reconnect grace for reversible restart-unknown scene poses. */
final class FrontierV3RestartDemandGrace {
    private static final long TICKS = 200L;
    private static final int MAX_PENDING = 4_096;
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SceneLeaseId, Long>> SINCE = new IdentityHashMap<>();

    private FrontierV3RestartDemandGrace() { }

    static boolean expired(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId, long gameTime) {
        Map<SceneLeaseId, Long> grace = SINCE.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        if (grace.size() >= MAX_PENDING && !grace.containsKey(leaseId)) return true;
        return gameTime - grace.computeIfAbsent(leaseId, ignored -> gameTime) >= TICKS;
    }

    static void reap(FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state) {
        Map<SceneLeaseId, Long> grace = SINCE.get(runtime);
        if (grace == null) return;
        grace.keySet().removeIf(id -> {
            var lease = state.sceneLeases().get(id);
            return lease == null || lease.status().name().equals("UNKNOWN_AFTER_RESTART") == false;
        });
        if (grace.isEmpty()) SINCE.remove(runtime);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { SINCE.remove(runtime); }
}
