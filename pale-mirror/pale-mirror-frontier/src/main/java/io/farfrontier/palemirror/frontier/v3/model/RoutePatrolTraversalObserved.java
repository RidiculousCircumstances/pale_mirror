package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One observed ordinary arrival on the patrol's single retained next ingress or route edge. */
public record RoutePatrolTraversalObserved(SubjectId taskId, SceneLeaseId leaseId, SubjectId actorId,
                                           BodyPosition observedBody) implements FrontierPayload {
    public RoutePatrolTraversalObserved {
        Objects.requireNonNull(taskId, "route-patrol traversal task");
        Objects.requireNonNull(leaseId, "route-patrol traversal lease");
        Objects.requireNonNull(actorId, "route-patrol traversal actor");
        Objects.requireNonNull(observedBody, "route-patrol observed body");
    }

    @Override public String type() { return "frontier.route_patrol_traversal_observed"; }
}
