package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Exact canonical body state of a resident or bioform, independent of HOT materialization. */
public record ActorLocation(BodyPosition body, ActorCondition condition) {
    public ActorLocation {
        Objects.requireNonNull(body, "actor body");
        Objects.requireNonNull(condition, "actor condition");
    }

    /** Fresh/bootstrap creation from one explicitly semantic support surface. */
    public static ActorLocation standingOn(SurfaceAnchor surface) {
        return new ActorLocation(BodyPosition.above(Objects.requireNonNull(surface, "actor surface")), ActorCondition.HEALTHY);
    }

    public ActorLocation withBody(BodyPosition nextBody) { return new ActorLocation(nextBody, condition); }

    public ActorLocation deadAt(BodyPosition nextBody) { return new ActorLocation(nextBody, ActorCondition.dead()); }

    /** Explicit semantic surface directly under this exact feet cell. */
    public SurfaceAnchor supportingSurface() { return body.supportingSurface(); }
}
