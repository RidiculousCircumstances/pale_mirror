package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;

/** One exact scout execution advances its current HOT/COLD deterministic patrol step. */
public record ScoutPatrolAdvanced(ActorExecutionId executionId, long goalRevision, long phase, SurfaceAnchor target,
                                  SurfaceAnchor priorSurface, Optional<HotArrival> hotArrival) implements FrontierPayload {
    public record HotArrival(ActorActuationId actuation, long scopeRevision) {
        public HotArrival {
            Objects.requireNonNull(actuation, "scout actuation");
            if (scopeRevision < 1L) throw new IllegalArgumentException("scout scope revision must be positive");
        }
    }
    public ScoutPatrolAdvanced {
        new ScoutPatrolStarted(executionId); // validate this family's complete nominal declaration
        if (phase < 0L) throw new IllegalArgumentException("scout patrol phase must be non-negative");
        if (goalRevision < 1L) throw new IllegalArgumentException("scout goal revision must be positive");
        Objects.requireNonNull(target, "scout patrol target");
        Objects.requireNonNull(priorSurface, "scout prior support");
        hotArrival = Objects.requireNonNull(hotArrival, "scout physical witness");
        hotArrival.ifPresent(arrival -> {
            if (!arrival.actuation().execution().equals(executionId))
                throw new IllegalArgumentException("scout arrival carries a foreign execution");
        });
    }

    public SubjectId scoutId() { return executionId.actorId(); }
    public BlockPosition position() { return target.support(); }
    public BlockPosition priorPosition() { return priorSurface.support(); }

    @Override public String type() { return "frontier.scout_patrol_advanced"; }
}
