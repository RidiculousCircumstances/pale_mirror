package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

public record RoutePatrolObstructionConfirmed(SubjectId taskId, BlockPosition position, ActorExecutionGroup executions) implements FrontierPayload {
    public RoutePatrolObstructionConfirmed {
        Objects.requireNonNull(executions, "patrol executions").requireDeclaration(ActorActivityKind.ROUTE_PATROL, taskId, executions.members().stream().map(ActorExecutionId::actorId).toList()); Objects.requireNonNull(taskId, "patrol task"); Objects.requireNonNull(position, "obstruction position"); }
    @Override public String type() { return "frontier.route_patrol_obstruction_confirmed"; }
}
