package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Bounded terminal compaction after the repaired crew has returned or lost its exact tools. */
public record RouteMaintenanceClosed(SubjectId maintenanceId) implements FrontierPayload {
    public RouteMaintenanceClosed { Objects.requireNonNull(maintenanceId, "route maintenance id"); }
    @Override public String type() { return "frontier.route_maintenance_closed"; }
}
