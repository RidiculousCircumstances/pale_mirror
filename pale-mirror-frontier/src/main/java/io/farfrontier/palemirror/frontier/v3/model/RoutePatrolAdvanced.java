package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

public record RoutePatrolAdvanced(SubjectId taskId, int routeIndex) implements FrontierPayload {
    public RoutePatrolAdvanced { Objects.requireNonNull(taskId, "patrol task"); if (routeIndex < 1) throw new IllegalArgumentException("patrol cursor must advance"); }
    @Override public String type() { return "frontier.route_patrol_advanced"; }
}
