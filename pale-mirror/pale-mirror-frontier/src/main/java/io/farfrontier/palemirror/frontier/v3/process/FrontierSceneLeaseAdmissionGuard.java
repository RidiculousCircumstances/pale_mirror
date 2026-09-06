package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseAdmission;

import java.util.Objects;

/**
 * Single generic command/replay boundary for any payload that proposes a scene lease.
 *
 * <p>Payload owners still validate their semantic actor, topology and outcome facts. This guard
 * owns only the SDK descriptor-family and hard-budget fence, so neither command dispatch nor
 * canonical model behavior needs a concrete-scene branch.</p>
 */
final class FrontierSceneLeaseAdmissionGuard {
    private FrontierSceneLeaseAdmissionGuard() { }

    static void require(FrontierPayload payload) {
        if (Objects.requireNonNull(payload, "scene admission payload") instanceof SceneLeaseAdmission admission) {
            FrontierDurationProcessDriverRegistry.requireSceneAdmission(admission.lease());
        }
    }
}
