package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact COLD engineering-crew movement observation; it cannot retarget or replace a member. */
public record RouteConstructionAssemblyAdvanced(SubjectId projectId, EngineeringWorkAssembly assembly) implements FrontierPayload {
    public RouteConstructionAssemblyAdvanced { Objects.requireNonNull(projectId, "construction assembly project"); Objects.requireNonNull(assembly, "construction assembly"); }
    @Override public String type() { return "frontier.route_construction_assembly_advanced"; }
}
