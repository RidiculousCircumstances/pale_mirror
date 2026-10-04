package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

public record RouteEngagementStarted(RouteEngagement engagement,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) implements FrontierPayload {
    public RouteEngagementStarted {
        Objects.requireNonNull(engagement, "route engagement");
        executions.requireDeclaration(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.ROUTE_INTERCEPTION,
                engagement.id(), engagement.attackerIds());
    }
    @Override public String type() { return "frontier.route_engagement_started"; }
}
