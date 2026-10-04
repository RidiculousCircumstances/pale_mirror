package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** One exact retained crew step, with captured physical authority for HOT arrivals. */
public record RouteConstructionAssemblyAdvanced(SubjectId projectId, EngineeringWorkAssembly assembly, ActorExecutionGroup executions,
        java.util.Optional<ActorExecutionGroup> workExecutions, java.util.Optional<EngineeringHotArrival> hotArrival) implements FrontierPayload {
    public RouteConstructionAssemblyAdvanced(SubjectId id, EngineeringWorkAssembly assembly, ActorExecutionGroup executions,
            java.util.Optional<ActorExecutionGroup> workExecutions) {
        this(id, assembly, executions, workExecutions, java.util.Optional.empty());
    }
    public RouteConstructionAssemblyAdvanced { Objects.requireNonNull(projectId, "construction assembly project"); Objects.requireNonNull(assembly, "construction assembly");
        EngineeringExecutionAuthority.requireOwner(executions, projectId, ActorActivityKind.ENGINEERING_ASSEMBLY);
        Objects.requireNonNull(workExecutions, "engineering work successor").ifPresent(group -> EngineeringExecutionAuthority.requireOwner(group, projectId, ActorActivityKind.ENGINEERING_WORK));
        Objects.requireNonNull(hotArrival, "engineering physical arrival").ifPresent(arrival -> {
            if (!executions.members().contains(arrival.actuation().execution()))
                throw new IllegalArgumentException("engineering arrival does not carry its captured execution");
        }); }
    @Override public String type() { return "frontier.route_construction_assembly_advanced"; }
}
