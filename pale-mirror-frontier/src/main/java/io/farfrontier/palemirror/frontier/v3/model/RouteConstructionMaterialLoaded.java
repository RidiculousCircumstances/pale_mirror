package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable semantic conversion of an observed chest removal into one exact COLD work cargo. */
record RouteConstructionMaterialLoaded(SubjectId projectId, CargoBatch cargo) implements FrontierPayload {
    RouteConstructionMaterialLoaded {
        Objects.requireNonNull(projectId, "route construction project id"); Objects.requireNonNull(cargo, "route construction cargo");
    }
    @Override public String type() { return "frontier.route_construction_material_loaded"; }
}
