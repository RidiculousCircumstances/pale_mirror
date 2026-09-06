package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

public record RouteEngagementStarted(RouteEngagement engagement) implements FrontierPayload {
    public RouteEngagementStarted { Objects.requireNonNull(engagement, "route engagement"); }
    @Override public String type() { return "frontier.route_engagement_started"; }
}
