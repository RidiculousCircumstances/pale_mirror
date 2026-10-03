package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** Durable declaration of the exact COLD approach before an engineering HOT scene may exist. */
public record RouteConstructionAssemblyStarted(SubjectId projectId, EngineeringWorkAssembly assembly, ActorExecutionGroup executions,
        java.util.Optional<ActorExecutionGroup> workExecutions) implements FrontierPayload {
    public RouteConstructionAssemblyStarted { Objects.requireNonNull(projectId, "construction assembly project"); Objects.requireNonNull(assembly, "construction assembly");
        EngineeringExecutionAuthority.requireOwner(executions, projectId, ActorActivityKind.ENGINEERING_ASSEMBLY);
        Objects.requireNonNull(workExecutions, "engineering work successor").ifPresent(group -> EngineeringExecutionAuthority.requireOwner(group, projectId, ActorActivityKind.ENGINEERING_WORK)); }
    @Override public String type() { return "frontier.route_construction_assembly_started"; }
}
