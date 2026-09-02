package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable conversion of one observed maintenance-chest decrement into one exact repair cargo. */
public record RouteMaintenanceMaterialLoaded(SubjectId maintenanceId, CargoBatch cargo) implements FrontierPayload {
    public RouteMaintenanceMaterialLoaded { Objects.requireNonNull(maintenanceId, "route maintenance id"); Objects.requireNonNull(cargo, "route maintenance cargo"); }
    @Override public String type() { return "frontier.route_maintenance_material_loaded"; }
}
