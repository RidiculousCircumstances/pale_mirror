package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

public record RouteEngagementTransition(SubjectId engagementId, RouteEngagementStatus status,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) implements FrontierPayload {
    public RouteEngagementTransition {
        Objects.requireNonNull(engagementId, "route engagement"); Objects.requireNonNull(status, "route engagement status");
        RouteEngagementExecutionAuthority.requireOwner(Objects.requireNonNull(executions, "interception cohort"), engagementId);
    }
    @Override public String type() { return "frontier.route_engagement_transition"; }
}
