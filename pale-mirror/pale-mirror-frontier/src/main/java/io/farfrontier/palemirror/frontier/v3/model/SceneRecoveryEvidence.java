package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Set;

/** Loaded-world proof that an UNKNOWN scene cannot reclaim its exact pre-restart bodies or carrier. */
public record SceneRecoveryEvidence(Set<SubjectId> missingActorIds, boolean missingCargoCarrier) {
    public SceneRecoveryEvidence {
        missingActorIds = Set.copyOf(missingActorIds);
        if (missingActorIds.isEmpty() && !missingCargoCarrier) {
            throw new IllegalArgumentException("scene recovery evidence must retain a missing actor or carrier");
        }
    }
}
