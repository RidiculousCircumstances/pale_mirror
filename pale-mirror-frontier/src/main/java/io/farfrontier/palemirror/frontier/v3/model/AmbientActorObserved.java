package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact non-terminal HOT-to-COLD capture for one non-leased ambient body. */
public record AmbientActorObserved(SubjectId actorId, BodyPosition body, FixedScalar health) implements FrontierPayload {
    public AmbientActorObserved {
        Objects.requireNonNull(actorId, "actor id");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(health, "health");
        if (health.compareTo(FixedScalar.ZERO) <= 0) throw new IllegalArgumentException("living ambient observation needs positive health");
    }

    @Override public String type() { return "frontier.ambient_actor_observed"; }
}
