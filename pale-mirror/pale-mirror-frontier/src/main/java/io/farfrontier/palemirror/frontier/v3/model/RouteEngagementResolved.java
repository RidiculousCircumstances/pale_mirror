package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Explicit terminal ownership event; it is never inferred from materialization absence. */
public record RouteEngagementResolved(SubjectId engagementId, RouteEngagementOutcome outcome,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) implements FrontierPayload {
    public RouteEngagementResolved {
        Objects.requireNonNull(engagementId, "engagement id"); Objects.requireNonNull(outcome, "engagement outcome");
        Objects.requireNonNull(executions, "exact resolved interception executions");
        for (var id : executions.members()) if (!id.activityOwnerId().equals(engagementId)
                || id.activityKind() != io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.ROUTE_INTERCEPTION)
            throw new IllegalArgumentException("interception resolution has a foreign kind or owner");
    }
    @Override public String type() { return "frontier.route_engagement_resolved"; }
}
