package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;

/** One COLD route start, causal checkpoint or arrival, never a physical observation. */
public record ActorMovementColdAdvanced(SubjectId actorId, long goalRevision, long atTick,
                                        Optional<SurfaceAnchor> arrivedSurface, ActorExecutionId executionId,
                                        Optional<PedestrianRouteReceipt> plannedRoute) implements FrontierPayload {
    public ActorMovementColdAdvanced {
        Objects.requireNonNull(actorId, "moving actor");
        Objects.requireNonNull(executionId, "movement execution authority");
        if (!actorId.equals(executionId.actorId())) throw new IllegalArgumentException("movement execution has foreign actor");
        arrivedSurface = Objects.requireNonNull(arrivedSurface, "optional segment arrival");
        plannedRoute = Objects.requireNonNull(plannedRoute, "optional accepted movement route");
        if (plannedRoute.isPresent() && arrivedSurface.isPresent()) throw new IllegalArgumentException("route start cannot also be arrival");
        if (goalRevision < 1L || atTick < 0L) throw new IllegalArgumentException("invalid movement clock or revision");
    }

    public ActorMovementColdAdvanced(SubjectId actorId, long goalRevision, long atTick,
                                     Optional<SurfaceAnchor> arrivedSurface, ActorExecutionId executionId) {
        this(actorId, goalRevision, atTick, arrivedSurface, executionId, Optional.empty());
    }

    public ActorMovementColdAdvanced(SubjectId actorId, long goalRevision, long atTick, ActorExecutionId executionId) {
        this(actorId, goalRevision, atTick, Optional.empty(), executionId);
    }

    @Override public String type() { return "frontier.actor_movement_cold_advanced"; }
}
