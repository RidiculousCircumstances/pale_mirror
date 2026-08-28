package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

record RouteEngagementTransition(SubjectId engagementId, RouteEngagementStatus status) implements FrontierPayload {
    RouteEngagementTransition {
        Objects.requireNonNull(engagementId, "route engagement"); Objects.requireNonNull(status, "route engagement status");
    }
    @Override public String type() { return "frontier.route_engagement_transition"; }
}
