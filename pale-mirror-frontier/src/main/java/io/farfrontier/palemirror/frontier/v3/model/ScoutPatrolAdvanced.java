package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** One exact unleased Scout advanced along its own deterministic hive patrol perimeter. */
public record ScoutPatrolAdvanced(SubjectId scoutId, long phase, BlockPosition position,
                                  Optional<BlockPosition> priorPosition) implements FrontierPayload {
    public ScoutPatrolAdvanced {
        Objects.requireNonNull(scoutId, "scout id");
        if (phase < 0L) throw new IllegalArgumentException("scout patrol phase must be non-negative");
        Objects.requireNonNull(position, "scout patrol position");
        priorPosition = Objects.requireNonNull(priorPosition, "scout patrol prior position");
    }

    /** Old WAL records had no predecessor and are reduced only as bounded COLD compatibility. */
    public ScoutPatrolAdvanced(SubjectId scoutId, long phase, BlockPosition position) {
        this(scoutId, phase, position, Optional.empty());
    }

    @Override public String type() { return "frontier.scout_patrol_advanced"; }
}
