package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable switch to a fully constructed replacement corridor. */
public record RouteTopologyCutover(SubjectId projectId) implements FrontierPayload {
    public RouteTopologyCutover { Objects.requireNonNull(projectId, "route construction project id"); }
    @Override public String type() { return "frontier.route_topology_cutover"; }
}
