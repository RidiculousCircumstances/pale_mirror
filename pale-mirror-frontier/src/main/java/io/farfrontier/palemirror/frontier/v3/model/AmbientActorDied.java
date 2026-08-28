package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Immutable evidence that one loaded, non-leased ambient actor actually died in Minecraft. */
public record AmbientActorDied(SubjectId actorId, BlockPosition position, String cause) implements FrontierPayload {
    public AmbientActorDied {
        Objects.requireNonNull(actorId, "actor id");
        Objects.requireNonNull(position, "position");
        if (cause == null || cause.isBlank()) throw new IllegalArgumentException("death cause must not be blank");
    }

    @Override public String type() { return "frontier.ambient_actor_died"; }
}
