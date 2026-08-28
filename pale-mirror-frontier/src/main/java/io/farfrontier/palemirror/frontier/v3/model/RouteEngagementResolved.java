package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Explicit terminal ownership event; it is never inferred from materialization absence. */
record RouteEngagementResolved(SubjectId engagementId, RouteEngagementOutcome outcome) implements FrontierPayload {
    RouteEngagementResolved {
        Objects.requireNonNull(engagementId, "engagement id"); Objects.requireNonNull(outcome, "engagement outcome");
    }
    @Override public String type() { return "frontier.route_engagement_resolved"; }
}
