package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact unleased Scout advanced along its own deterministic hive patrol perimeter. */
record ScoutPatrolAdvanced(SubjectId scoutId, long phase, BlockPosition position) implements FrontierPayload {
    ScoutPatrolAdvanced {
        Objects.requireNonNull(scoutId, "scout id");
        if (phase < 0L) throw new IllegalArgumentException("scout patrol phase must be non-negative");
        Objects.requireNonNull(position, "scout patrol position");
    }

    @Override public String type() { return "frontier.scout_patrol_advanced"; }
}
