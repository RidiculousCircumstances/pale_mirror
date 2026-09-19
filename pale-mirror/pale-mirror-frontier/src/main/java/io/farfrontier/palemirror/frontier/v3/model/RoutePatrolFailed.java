package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

public record RoutePatrolFailed(SubjectId taskId, DiagnosticTuple diagnostic) implements FrontierPayload {
    public RoutePatrolFailed { Objects.requireNonNull(taskId, "patrol task"); diagnostic = Objects.requireNonNull(diagnostic, "patrol failure diagnostic");
        if (diagnostic.reason() != DiagnosticReason.ROUTE_PATROL_MEMBER_LOST || !diagnostic.owner().id().equals(taskId)
                || !diagnostic.subject().id().equals(taskId)) throw new IllegalArgumentException("patrol failure has a foreign diagnostic tuple"); }
    @Override public String type() { return "frontier.route_patrol_failed"; }
}
