package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable declaration of the exact COLD approach for one retained repair crew. */
public record RouteMaintenanceAssemblyStarted(SubjectId maintenanceId, EngineeringWorkAssembly assembly) implements FrontierPayload {
    public RouteMaintenanceAssemblyStarted { Objects.requireNonNull(maintenanceId, "route maintenance id"); Objects.requireNonNull(assembly, "route maintenance assembly"); }
    @Override public String type() { return "frontier.route_maintenance_assembly_started"; }
}
