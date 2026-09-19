package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Named patrol boundaries retain a typed blocked explanation for one exact task. */
public enum RoutePatrolDiagnosticProducer {
    NO_OPEN_RETAINED_EDGE(RoutePatrolBlockReason.NO_OPEN_RETAINED_EDGE),
    MISSING_OWNED_BODY(RoutePatrolBlockReason.MISSING_OWNED_BODY),
    CHANGED_OWNED_BODY(RoutePatrolBlockReason.CHANGED_OWNED_BODY),
    OCCUPIED_NEXT_BODY(RoutePatrolBlockReason.OCCUPIED_NEXT_BODY),
    DAMAGED_SUPPORT(RoutePatrolBlockReason.DAMAGED_SUPPORT),
    PLAYER_OR_WORLD_OBSTRUCTION(RoutePatrolBlockReason.PLAYER_OR_WORLD_OBSTRUCTION),
    RECOVERY_UNRESOLVED(RoutePatrolBlockReason.RECOVERY_UNRESOLVED);
    private final RoutePatrolBlockReason reason;
    RoutePatrolDiagnosticProducer(RoutePatrolBlockReason reason) { this.reason = reason; }
    public RoutePatrolBlocked create(SubjectId taskId) {
        return new RoutePatrolBlocked(taskId, reason, new DiagnosticTuple(DiagnosticReason.ROUTE_PATROL_BLOCKED,
                DiagnosticCategory.WAIT_OR_BLOCKED, new DiagnosticOwner(DiagnosticOwnerKind.ROUTE_PATROL, taskId),
                new DiagnosticSubject(DiagnosticSubjectKind.ROUTE_MEMBER, taskId), DiagnosticDisposition.RETRY));
    }
}
