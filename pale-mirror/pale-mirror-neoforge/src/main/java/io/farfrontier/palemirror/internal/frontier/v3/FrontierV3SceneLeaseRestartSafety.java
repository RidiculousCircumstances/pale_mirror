package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;

/** Restart-only quarantine for scene bodies; recovery requires later loaded-world inspection. */
final class FrontierV3SceneLeaseRestartSafety {
    private FrontierV3SceneLeaseRestartSafety() { }

    static int quarantineActiveLeases(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return 0;
        int count = 0;
        for (var lease : state.sceneLeases().values().stream().sorted(java.util.Comparator.comparing(value -> value.id().value())).toList()) {
            if (lease.status() == SceneLeaseStatus.PREPARED || lease.status() == SceneLeaseStatus.HOT || lease.status() == SceneLeaseStatus.DRAINING) {
                FrontierV3CommandSubmission.submit(runtime, "scene-restart-unknown", lease.id().value(),
                        new SceneLeaseTransition(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
                count++;
            }
        }
        return count;
    }
}
