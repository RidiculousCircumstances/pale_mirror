package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.Objects;

/** Family-declared presentation target. It grants no route, body or operation authority. */
public record ActorActivityAmbientTarget(AmbientGoalKind kind, SurfaceAnchor station) {
    public ActorActivityAmbientTarget { Objects.requireNonNull(kind); Objects.requireNonNull(station); }
}
