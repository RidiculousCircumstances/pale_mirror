package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/** One exact retained crew step; it cannot retarget the repair or replace a member. */
public record RouteMaintenanceAssemblyAdvanced(SubjectId maintenanceId, EngineeringWorkAssembly assembly, ActorExecutionGroup executions,
        java.util.Optional<ActorExecutionGroup> workExecutions) implements FrontierPayload {
    public RouteMaintenanceAssemblyAdvanced { Objects.requireNonNull(maintenanceId, "route maintenance id"); Objects.requireNonNull(assembly, "route maintenance assembly");
        EngineeringExecutionAuthority.requireOwner(executions, maintenanceId, ActorActivityKind.ENGINEERING_ASSEMBLY);
        Objects.requireNonNull(workExecutions, "engineering work successor").ifPresent(group -> EngineeringExecutionAuthority.requireOwner(group, maintenanceId, ActorActivityKind.ENGINEERING_WORK)); }
    @Override public String type() { return "frontier.route_maintenance_assembly_advanced"; }
}
