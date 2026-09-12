package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** A retained patrol cannot advance its next declared ingress or route edge. */
public record RoutePatrolBlocked(SubjectId taskId, RoutePatrolBlockReason reason) implements FrontierPayload {
    public RoutePatrolBlocked { Objects.requireNonNull(taskId, "blocked patrol task"); Objects.requireNonNull(reason, "patrol block reason"); }
    @Override public String type() { return "frontier.route_patrol_blocked"; }
}
