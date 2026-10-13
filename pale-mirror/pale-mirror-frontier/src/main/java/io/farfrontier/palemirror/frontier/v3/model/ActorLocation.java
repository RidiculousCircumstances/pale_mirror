package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile;

import java.util.Objects;

/** Exact canonical body state of a resident or bioform, independent of HOT materialization. */
public record ActorLocation(BodyPosition body, ActorCondition condition, ActorKind kind,
                            UnitNavigationProfile navigationProfile) {
    public ActorLocation {
        Objects.requireNonNull(body, "actor body");
        Objects.requireNonNull(condition, "actor condition");
        Objects.requireNonNull(kind, "declared actor kind");
        Objects.requireNonNull(navigationProfile, "producer-declared navigation profile");
    }

    /** Fresh/bootstrap creation from one explicitly semantic support surface. */
    public static ActorLocation standingOn(SurfaceAnchor surface, ActorKind kind,
            UnitNavigationProfile profile) {
        return new ActorLocation(BodyPosition.above(Objects.requireNonNull(surface, "actor surface")), ActorCondition.HEALTHY, kind, profile);
    }

    public ActorLocation withBody(BodyPosition nextBody) { return new ActorLocation(nextBody, condition, kind, navigationProfile); }

    public ActorLocation deadAt(BodyPosition nextBody) { return new ActorLocation(nextBody, ActorCondition.dead(), kind, navigationProfile); }

    public ActorLocation withObserved(BodyPosition nextBody, ActorCondition nextCondition) {
        return new ActorLocation(nextBody, nextCondition, kind, navigationProfile);
    }

    /** Explicit semantic surface directly under this exact feet cell. */
    public SurfaceAnchor supportingSurface() { return body.supportingSurface(); }
}
