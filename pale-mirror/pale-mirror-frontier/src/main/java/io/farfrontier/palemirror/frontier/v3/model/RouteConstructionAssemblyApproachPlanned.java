package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Objects;

/** Construction owner retains one cooperatively selected approach, not a movement receipt. */
public record RouteConstructionAssemblyApproachPlanned(SubjectId projectId, ActorExecutionGroup executions,
                                                      EngineeringApproachSelection selection) implements FrontierPayload {
    public RouteConstructionAssemblyApproachPlanned {
        Objects.requireNonNull(projectId); Objects.requireNonNull(selection);
        EngineeringExecutionAuthority.requireOwner(executions, projectId, ActorActivityKind.ENGINEERING_ASSEMBLY);
        if (executions.members().stream().noneMatch(id -> id.actorId().equals(selection.actorId())))
            throw new IllegalArgumentException("construction approach has a foreign selected member");
    }
    @Override public String type() { return "frontier.route_construction_assembly_approach_planned"; }
}
