package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Exact canonical body state of a resident or bioform, independent of HOT materialization. */
public record ActorLocation(BodyPosition body, ActorCondition condition, ActorKind kind) {
    public ActorLocation {
        Objects.requireNonNull(body, "actor body");
        Objects.requireNonNull(condition, "actor condition");
        Objects.requireNonNull(kind, "declared actor kind");
    }

    /** Fresh/bootstrap creation from one explicitly semantic support surface. */
    public static ActorLocation standingOn(SurfaceAnchor surface, ActorKind kind) {
        return new ActorLocation(BodyPosition.above(Objects.requireNonNull(surface, "actor surface")), ActorCondition.HEALTHY, kind);
    }

    public ActorLocation withBody(BodyPosition nextBody) { return new ActorLocation(nextBody, condition, kind); }

    public ActorLocation deadAt(BodyPosition nextBody) { return new ActorLocation(nextBody, ActorCondition.dead(), kind); }

    public ActorLocation withObserved(BodyPosition nextBody, ActorCondition nextCondition) {
        return new ActorLocation(nextBody, nextCondition, kind);
    }

    /** Explicit semantic surface directly under this exact feet cell. */
    public SurfaceAnchor supportingSurface() { return body.supportingSurface(); }
}
