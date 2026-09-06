package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable admission of one exact retained-route repair; it cannot alter topology. */
public record RouteMaintenanceStarted(RouteMaintenance maintenance) implements FrontierPayload {
    public RouteMaintenanceStarted { Objects.requireNonNull(maintenance, "route maintenance"); }
    @Override public String type() { return "frontier.route_maintenance_started"; }
}
