package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

/** Pure immutable read-model compiler. */
@FunctionalInterface
public interface ProjectionMapper<S, P extends FrontierProjection> {
    P project(S state, WorldId worldId, Revision revision, SimInstant instant, ProjectionQuery query);
}
