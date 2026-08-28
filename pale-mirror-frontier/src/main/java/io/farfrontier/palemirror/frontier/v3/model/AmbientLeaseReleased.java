package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Durable capture of a living exact ambient body before it returns to COLD execution. */
public record AmbientLeaseReleased(SubjectId actorId, BlockPosition position, FixedScalar health) implements FrontierPayload {
    public AmbientLeaseReleased {
        Objects.requireNonNull(actorId, "actor id"); Objects.requireNonNull(position, "position"); Objects.requireNonNull(health, "health");
        if (health.raw() <= 0L) throw new IllegalArgumentException("ambient release must retain living health");
    }
    @Override public String type() { return "frontier.ambient_lease_released"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
