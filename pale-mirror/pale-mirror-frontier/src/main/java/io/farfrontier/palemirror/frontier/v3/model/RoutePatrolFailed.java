package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

public record RoutePatrolFailed(SubjectId taskId) implements FrontierPayload {
    public RoutePatrolFailed { Objects.requireNonNull(taskId, "patrol task"); }
    @Override public String type() { return "frontier.route_patrol_failed"; }
}
