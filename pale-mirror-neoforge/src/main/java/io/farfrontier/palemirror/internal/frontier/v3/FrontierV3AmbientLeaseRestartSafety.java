package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;

/** Restart-only recovery boundary for exact ambient actor leases; no Minecraft class is required. */
final class FrontierV3AmbientLeaseRestartSafety {
    private FrontierV3AmbientLeaseRestartSafety() { }
    static int quarantineActiveLeases(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return 0;
        int count = 0;
        for (var lease : state.ambientLeases().values().stream().sorted(java.util.Comparator.comparing(value -> value.actorId().value())).toList()) {
            if (lease.status() == AmbientLeaseStatus.PREPARED || lease.status() == AmbientLeaseStatus.HOT || lease.status() == AmbientLeaseStatus.DRAINING) {
                FrontierV3CommandSubmission.submit(runtime, "ambient-restart-unknown", lease.actorId().value(),
                        new AmbientLeaseTransition(lease.actorId(), AmbientLeaseStatus.UNKNOWN_AFTER_RESTART));
                count++;
            }
        }
        return count;
    }
}
