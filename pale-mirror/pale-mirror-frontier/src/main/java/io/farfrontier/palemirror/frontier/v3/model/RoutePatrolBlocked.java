package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** A retained patrol cannot advance its next declared ingress or route edge. */
public record RoutePatrolBlocked(SubjectId taskId, RoutePatrolBlockReason reason, DiagnosticTuple diagnostic) implements FrontierPayload {
    public RoutePatrolBlocked { Objects.requireNonNull(taskId, "blocked patrol task"); Objects.requireNonNull(reason, "patrol block reason"); diagnostic = Objects.requireNonNull(diagnostic, "patrol block diagnostic");
        if (diagnostic.reason() != DiagnosticReason.ROUTE_PATROL_BLOCKED || !diagnostic.owner().id().equals(taskId) || !diagnostic.subject().id().equals(taskId)) throw new IllegalArgumentException("patrol block has a foreign diagnostic tuple"); }
    @Override public String type() { return "frontier.route_patrol_blocked"; }
}
