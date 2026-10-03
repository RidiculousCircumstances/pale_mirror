package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** Durable admission of an inactive candidate corridor; it is not a topology change. */
public record RouteConstructionStarted(RouteConstruction project, java.util.Optional<ActorExecutionGroup> executions) implements FrontierPayload {
    public RouteConstructionStarted { Objects.requireNonNull(project, "route construction project"); Objects.requireNonNull(executions, "engineering executions");
        if (project.engineeringTeam().isPresent() != executions.isPresent()) throw new IllegalArgumentException("construction must declare exactly its crew");
        executions.ifPresent(group -> group.requireDeclaration(ActorActivityKind.ENGINEERING_ASSEMBLY, project.id(), project.engineeringTeam().orElseThrow().memberIds())); }
    @Override public String type() { return "frontier.route_construction_started"; }
}
