package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Objects;

/** One observed full-column edge; partial guard arrival cannot advance a patrol cursor. */
public record RoutePatrolFormationObserved(SubjectId taskId, SceneLeaseId leaseId, Map<SubjectId, BodyPosition> bodies) implements FrontierPayload {
    public RoutePatrolFormationObserved {
        taskId = Objects.requireNonNull(taskId, "patrol task"); leaseId = Objects.requireNonNull(leaseId, "patrol lease");
        bodies = Map.copyOf(Objects.requireNonNull(bodies, "patrol formation bodies"));
        if (bodies.size() < 2 || bodies.values().stream().distinct().count() != bodies.size()) throw new IllegalArgumentException("patrol formation evidence");
    }
    @Override public String type() { return "frontier.route_patrol_formation_observed"; }
}
