package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Objects;

/** Maintenance owner retains one cooperatively selected approach, not a movement receipt. */
public record RouteMaintenanceAssemblyApproachPlanned(SubjectId maintenanceId, ActorExecutionGroup executions,
                                                     EngineeringApproachSelection selection) implements FrontierPayload {
    public RouteMaintenanceAssemblyApproachPlanned {
        Objects.requireNonNull(maintenanceId); Objects.requireNonNull(selection);
        EngineeringExecutionAuthority.requireOwner(executions, maintenanceId, ActorActivityKind.ENGINEERING_ASSEMBLY);
        if (executions.members().stream().noneMatch(id -> id.actorId().equals(selection.actorId())))
            throw new IllegalArgumentException("maintenance approach has a foreign selected member");
    }
    @Override public String type() { return "frontier.route_maintenance_assembly_approach_planned"; }
}
