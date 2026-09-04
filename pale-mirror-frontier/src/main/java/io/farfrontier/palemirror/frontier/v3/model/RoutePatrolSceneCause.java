package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact retained route-patrol task temporarily owned by a loaded scene. */
public record RoutePatrolSceneCause(SubjectId taskId) implements SceneCause {
    public RoutePatrolSceneCause {
        Objects.requireNonNull(taskId, "route-patrol scene task");
    }

    @Override public SceneCauseKind kind() { return SceneCauseKind.ROUTE_PATROL; }
}
