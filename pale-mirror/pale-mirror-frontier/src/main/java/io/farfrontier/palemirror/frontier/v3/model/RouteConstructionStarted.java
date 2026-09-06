package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable admission of an inactive candidate corridor; it is not a topology change. */
public record RouteConstructionStarted(RouteConstruction project) implements FrontierPayload {
    public RouteConstructionStarted { Objects.requireNonNull(project, "route construction project"); }
    @Override public String type() { return "frontier.route_construction_started"; }
}
