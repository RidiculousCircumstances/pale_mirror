package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
/** Canonical COLD formation edge; no physical body coordinate is fabricated. */
public record RoutePatrolFormationAdvanced(SubjectId taskId, PatrolFormationStep predecessor, ActorExecutionGroup executions) implements FrontierPayload {
    public RoutePatrolFormationAdvanced {
        Objects.requireNonNull(executions, "patrol executions").requireDeclaration(ActorActivityKind.ROUTE_PATROL, taskId,
                Objects.requireNonNull(predecessor, "patrol predecessor").members().keySet()); Objects.requireNonNull(taskId, "patrol task"); }
    @Override public String type() { return "frontier.route_patrol_formation_advanced"; }
}
