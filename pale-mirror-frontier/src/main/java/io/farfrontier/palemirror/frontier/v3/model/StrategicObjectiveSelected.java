package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Immutable receipt of deterministic utility selection for one side. */
record StrategicObjectiveSelected(StrategicObjective objective) implements FrontierPayload {
    StrategicObjectiveSelected { Objects.requireNonNull(objective, "strategic objective"); }
    @Override public String type() { return "frontier.strategic_objective_selected"; }
}
