package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;

/** One exact scout execution advances its current HOT/COLD deterministic patrol step. */
public record ScoutPatrolAdvanced(ActorExecutionId executionId, long phase, BlockPosition position,
                                  BlockPosition priorPosition) implements FrontierPayload {
    public ScoutPatrolAdvanced {
        new ScoutPatrolStarted(executionId); // validate this family's complete nominal declaration
        if (phase < 0L) throw new IllegalArgumentException("scout patrol phase must be non-negative");
        Objects.requireNonNull(position, "scout patrol position");
        priorPosition = Objects.requireNonNull(priorPosition, "scout patrol prior position");
    }

    public SubjectId scoutId() { return executionId.actorId(); }

    @Override public String type() { return "frontier.scout_patrol_advanced"; }
}
