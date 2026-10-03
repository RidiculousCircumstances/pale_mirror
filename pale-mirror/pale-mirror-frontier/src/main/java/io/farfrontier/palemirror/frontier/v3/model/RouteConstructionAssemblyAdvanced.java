package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** One exact COLD engineering-crew movement observation; it cannot retarget or replace a member. */
public record RouteConstructionAssemblyAdvanced(SubjectId projectId, EngineeringWorkAssembly assembly, ActorExecutionGroup executions,
        java.util.Optional<ActorExecutionGroup> workExecutions) implements FrontierPayload {
    public RouteConstructionAssemblyAdvanced { Objects.requireNonNull(projectId, "construction assembly project"); Objects.requireNonNull(assembly, "construction assembly");
        EngineeringExecutionAuthority.requireOwner(executions, projectId, ActorActivityKind.ENGINEERING_ASSEMBLY);
        Objects.requireNonNull(workExecutions, "engineering work successor").ifPresent(group -> EngineeringExecutionAuthority.requireOwner(group, projectId, ActorActivityKind.ENGINEERING_WORK)); }
    @Override public String type() { return "frontier.route_construction_assembly_advanced"; }
}
