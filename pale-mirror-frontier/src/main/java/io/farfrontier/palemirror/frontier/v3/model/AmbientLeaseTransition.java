package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Durable lifecycle transition for one exact ambient actor. */
public record AmbientLeaseTransition(SubjectId actorId, AmbientLeaseStatus status) implements FrontierPayload {
    public AmbientLeaseTransition {
        Objects.requireNonNull(actorId, "actor id"); Objects.requireNonNull(status, "ambient lease status");
        if (status == AmbientLeaseStatus.PREPARED || status == AmbientLeaseStatus.CLOSED) {
            throw new IllegalArgumentException("ambient lease transition must target an active or recovery status");
        }
    }
    @Override public String type() { return "frontier.ambient_lease_transition"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
