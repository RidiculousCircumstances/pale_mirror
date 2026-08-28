package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

record RouteEngagementStarted(RouteEngagement engagement) implements FrontierPayload {
    RouteEngagementStarted { Objects.requireNonNull(engagement, "route engagement"); }
    @Override public String type() { return "frontier.route_engagement_started"; }
}
