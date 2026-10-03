package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Exact roster loss is a terminal patrol outcome, never a free-text failed state. */
public final class RoutePatrolFailureDiagnosticProducer {
    private RoutePatrolFailureDiagnosticProducer() { }

    public static RoutePatrolFailed memberLost(SubjectId taskId, io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) {
        return new RoutePatrolFailed(taskId, new DiagnosticTuple(DiagnosticReason.ROUTE_PATROL_MEMBER_LOST,
                DiagnosticCategory.DOMAIN_DISRUPTION, new DiagnosticOwner(DiagnosticOwnerKind.ROUTE_PATROL, taskId),
                new DiagnosticSubject(DiagnosticSubjectKind.ROUTE_MEMBER, taskId), DiagnosticDisposition.INSPECT), executions);
    }
}
