package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

public record RoutePatrolAdvanced(SubjectId taskId, SubjectId actorId) implements FrontierPayload {
    public RoutePatrolAdvanced { Objects.requireNonNull(taskId, "patrol task"); Objects.requireNonNull(actorId, "patrol advancing actor"); }
    @Override public String type() { return "frontier.route_patrol_advanced"; }
}
