package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflicted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflictReason;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;

/**
 * Restart-only fail-closed boundary for a cocoon block whose removal may have happened before
 * the canonical release receipt was persisted.
 */
final class FrontierV3HiveMobilizationRestartSafety {
    private FrontierV3HiveMobilizationRestartSafety() { }

    static int quarantineReleasingMobilizations(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return 0;
        int count = 0;
        for (var mobilization : state.hiveColony().mobilizations().values().stream()
                .filter(value -> value.status() == HiveMobilizationStatus.RELEASING)
                .sorted(java.util.Comparator.comparing(value -> value.id().value())).toList()) {
            // There is no safe replay or absence-as-success rule for a removed block.  A later
            // naturally loaded inspection can explain the visible aftermath, but this exact
            // group must not make a body or progress from an uninspected physical effect.
            FrontierV3CommandSubmission.submit(runtime, "hive-mobilization-restart-unknown", mobilization.id().value(),
                    new HiveMobilizationConflicted(mobilization.id(), HiveMobilizationConflictReason.UNKNOWN_AFTER_RESTART));
            count++;
        }
        return count;
    }
}
