package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import java.util.Map;
import java.util.Objects;

/** Exact producer-selected approaches; adoption does not move a body or award an inspection edge. */
public record RoutePatrolSpatialPlanned(SubjectId taskId, PatrolFormationStep predecessor,
        Map<SubjectId, TraversalRejoin> approaches, ActorExecutionGroup executions) implements FrontierPayload {
    public RoutePatrolSpatialPlanned {
        Objects.requireNonNull(taskId); Objects.requireNonNull(predecessor);
        approaches = Map.copyOf(Objects.requireNonNull(approaches));
        if (approaches.isEmpty() || !predecessor.members().keySet().containsAll(approaches.keySet()))
            throw new IllegalArgumentException("patrol approach selection requires its nonempty exact member subset");
        Objects.requireNonNull(executions).requireDeclaration(ActorActivityKind.ROUTE_PATROL, taskId, predecessor.members().keySet());
    }
    @Override public String type() { return "frontier.route_patrol_spatial_planned"; }
}
