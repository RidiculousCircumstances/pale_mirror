package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Objects;

/** One observed full-column edge; partial guard arrival cannot advance a patrol cursor. */
public record RoutePatrolFormationObserved(SubjectId taskId, SceneLeaseId leaseId, long leaseRevision, Map<SubjectId, BodyPosition> bodies,
                                           PatrolFormationStep predecessor, ActorActuationGroup actuations) implements FrontierPayload {
    public RoutePatrolFormationObserved {
        taskId = Objects.requireNonNull(taskId, "patrol task"); leaseId = Objects.requireNonNull(leaseId, "patrol lease");
        bodies = Map.copyOf(Objects.requireNonNull(bodies, "patrol formation bodies"));
        Objects.requireNonNull(actuations, "captured patrol actuations").executions()
                .requireDeclaration(ActorActivityKind.ROUTE_PATROL, taskId, bodies.keySet());
        if (leaseRevision < 0L || !Objects.requireNonNull(predecessor, "patrol predecessor").members().keySet().equals(bodies.keySet()))
            throw new IllegalArgumentException("patrol observation lost its scope or predecessor cohort");
        if (bodies.size() < 2 || bodies.values().stream().distinct().count() != bodies.size()) throw new IllegalArgumentException("patrol formation evidence");
    }
    public ActorExecutionGroup executions() { return actuations.executions(); }
    @Override public String type() { return "frontier.route_patrol_formation_observed"; }
}
