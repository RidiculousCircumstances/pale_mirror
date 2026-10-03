package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

public record RoutePatrolFailed(SubjectId taskId, DiagnosticTuple diagnostic, ActorExecutionGroup executions) implements FrontierPayload {
    public RoutePatrolFailed {
        Objects.requireNonNull(executions, "patrol executions").requireDeclaration(ActorActivityKind.ROUTE_PATROL, taskId, executions.members().stream().map(ActorExecutionId::actorId).toList()); Objects.requireNonNull(taskId, "patrol task"); diagnostic = Objects.requireNonNull(diagnostic, "patrol failure diagnostic");
        if (diagnostic.reason() != DiagnosticReason.ROUTE_PATROL_MEMBER_LOST || !diagnostic.owner().id().equals(taskId)
                || !diagnostic.subject().id().equals(taskId)) throw new IllegalArgumentException("patrol failure has a foreign diagnostic tuple"); }
    @Override public String type() { return "frontier.route_patrol_failed"; }
}
