package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

import java.util.Objects;
import java.util.Optional;

/** One COLD route start, causal checkpoint or arrival, never a physical observation. */
public record ActorMovementColdAdvanced(SubjectId actorId, long goalRevision, long atTick,
                                        Optional<SurfaceAnchor> arrivedSurface) implements FrontierPayload {
    public ActorMovementColdAdvanced {
        Objects.requireNonNull(actorId, "moving actor");
        arrivedSurface = Objects.requireNonNull(arrivedSurface, "optional segment arrival");
        if (goalRevision < 1L || atTick < 0L) throw new IllegalArgumentException("invalid movement clock or revision");
    }

    public ActorMovementColdAdvanced(SubjectId actorId, long goalRevision, long atTick) {
        this(actorId, goalRevision, atTick, Optional.empty());
    }

    @Override public String type() { return "frontier.actor_movement_cold_advanced"; }
}
