package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Set;

/** Loaded-world proof that an UNKNOWN scene cannot reclaim its exact pre-restart bodies. */
public record SceneRecoveryEvidence(Set<SubjectId> missingActorIds) {
    public SceneRecoveryEvidence {
        missingActorIds = Set.copyOf(missingActorIds);
        if (missingActorIds.isEmpty()) {
            throw new IllegalArgumentException("scene recovery evidence must retain a missing actor");
        }
    }
}
