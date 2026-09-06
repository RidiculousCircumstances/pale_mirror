package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable declaration of the exact COLD approach before an engineering HOT scene may exist. */
public record RouteConstructionAssemblyStarted(SubjectId projectId, EngineeringWorkAssembly assembly) implements FrontierPayload {
    public RouteConstructionAssemblyStarted { Objects.requireNonNull(projectId, "construction assembly project"); Objects.requireNonNull(assembly, "construction assembly"); }
    @Override public String type() { return "frontier.route_construction_assembly_started"; }
}
