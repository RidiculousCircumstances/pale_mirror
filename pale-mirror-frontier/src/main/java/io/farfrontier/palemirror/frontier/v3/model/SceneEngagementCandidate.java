package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Immutable, exact COLD engagement projection available to the loaded-chunk HOT adapter. */
public record SceneEngagementCandidate(SubjectId engagementId, SubjectId operationId, SubjectId cargoId,
                                      BlockPosition handoffPosition, BlockPosition cargoPosition, List<SubjectId> actorIds) {
    public SceneEngagementCandidate {
        Objects.requireNonNull(engagementId, "engagement id"); Objects.requireNonNull(operationId, "operation id");
        Objects.requireNonNull(cargoId, "cargo id"); Objects.requireNonNull(handoffPosition, "handoff position");
        Objects.requireNonNull(cargoPosition, "cargo position"); actorIds = List.copyOf(actorIds);
        if (actorIds.size() < 2 || actorIds.size() > 24 || actorIds.stream().distinct().count() != actorIds.size()) {
            throw new IllegalArgumentException("scene engagement must retain two to twenty-four distinct actors");
        }
    }
}
