package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** Durable admission of one exact retained-route repair; it cannot alter topology. */
public record RouteMaintenanceStarted(RouteMaintenance maintenance, ActorExecutionGroup executions) implements FrontierPayload {
    public RouteMaintenanceStarted { Objects.requireNonNull(maintenance, "route maintenance"); Objects.requireNonNull(executions, "engineering executions");
        executions.requireDeclaration(ActorActivityKind.ENGINEERING_ASSEMBLY, maintenance.id(), maintenance.team().memberIds()); }
    @Override public String type() { return "frontier.route_maintenance_started"; }
}
