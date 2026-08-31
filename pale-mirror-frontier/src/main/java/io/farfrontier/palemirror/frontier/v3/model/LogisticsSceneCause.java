package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Exact operation/cargo binding for the pre-existing caravan and interception scene. */
public record LogisticsSceneCause(SubjectId operationId, SubjectId cargoId, Optional<SubjectId> engagementId,
                                  BlockPosition cargoPosition) implements SceneCause {
    public LogisticsSceneCause {
        Objects.requireNonNull(operationId, "scene operation");
        Objects.requireNonNull(cargoId, "scene cargo");
        engagementId = Objects.requireNonNull(engagementId, "scene engagement");
        Objects.requireNonNull(cargoPosition, "scene cargo position");
    }
}
