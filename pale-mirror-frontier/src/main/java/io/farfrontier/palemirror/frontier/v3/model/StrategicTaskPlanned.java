package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Immutable receipt of HTN-like task admission for an existing strategic objective. */
public record StrategicTaskPlanned(StrategicTask task) implements FrontierPayload {
    public StrategicTaskPlanned { Objects.requireNonNull(task, "strategic task"); }
    @Override public String type() { return "frontier.strategic_task_planned"; }
}
