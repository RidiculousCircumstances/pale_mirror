package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

public record RoutePatrolStarted(RoutePatrol patrol, ActorExecutionGroup executions) implements FrontierPayload {
    public RoutePatrolStarted {
        Objects.requireNonNull(executions, "patrol executions").requireDeclaration(ActorActivityKind.ROUTE_PATROL, patrol.taskId(), patrol.memberIds()); Objects.requireNonNull(patrol, "route patrol"); }
    @Override public String type() { return "frontier.route_patrol_started"; }
}
