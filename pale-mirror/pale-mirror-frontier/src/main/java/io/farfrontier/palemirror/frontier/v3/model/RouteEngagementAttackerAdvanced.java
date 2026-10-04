package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

public record RouteEngagementAttackerAdvanced(SubjectId engagementId, SubjectId attackerId, int routeIndex,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution) implements FrontierPayload {
    public RouteEngagementAttackerAdvanced {
        Objects.requireNonNull(engagementId, "route engagement"); Objects.requireNonNull(attackerId, "engagement attacker");
        if (routeIndex < 1) throw new IllegalArgumentException("engagement route cursor must advance");
        Objects.requireNonNull(execution, "exact attacker execution");
        if (!execution.actorId().equals(attackerId) || !execution.activityOwnerId().equals(engagementId)
                || execution.activityKind() != io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.ROUTE_INTERCEPTION)
            throw new IllegalArgumentException("interception advance has a foreign actor, kind or owner");
    }
    @Override public String type() { return "frontier.route_engagement_attacker_advanced"; }
}
