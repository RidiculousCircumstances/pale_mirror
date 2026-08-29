package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Set;

/** Exact loaded-world observation that prevents an UNKNOWN supply scene from being reclaimed. */
public record SceneLeaseRecoveryUnresolved(SceneLeaseId leaseId, Set<SubjectId> missingActorIds,
                                           boolean missingCargoCarrier) implements FrontierPayload {
    public SceneLeaseRecoveryUnresolved {
        Objects.requireNonNull(leaseId, "scene recovery lease");
        missingActorIds = Set.copyOf(missingActorIds);
        if (missingActorIds.isEmpty() && !missingCargoCarrier) {
            throw new IllegalArgumentException("scene recovery must name a missing actor or carrier");
        }
    }
    @Override public String type() { return "frontier.scene_lease_recovery_unresolved"; }
}
