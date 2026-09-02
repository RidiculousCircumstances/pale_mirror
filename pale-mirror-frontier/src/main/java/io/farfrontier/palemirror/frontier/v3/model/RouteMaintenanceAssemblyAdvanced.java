package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact retained crew step; it cannot retarget the repair or replace a member. */
public record RouteMaintenanceAssemblyAdvanced(SubjectId maintenanceId, EngineeringWorkAssembly assembly) implements FrontierPayload {
    public RouteMaintenanceAssemblyAdvanced { Objects.requireNonNull(maintenanceId, "route maintenance id"); Objects.requireNonNull(assembly, "route maintenance assembly"); }
    @Override public String type() { return "frontier.route_maintenance_assembly_advanced"; }
}
