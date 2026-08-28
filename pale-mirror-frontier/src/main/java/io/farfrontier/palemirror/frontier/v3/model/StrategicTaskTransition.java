package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable lifecycle evidence for one already-admitted strategic task. */
record StrategicTaskTransition(SubjectId taskId, StrategicTaskStatus status) implements FrontierPayload {
    StrategicTaskTransition { Objects.requireNonNull(taskId, "strategic task id"); Objects.requireNonNull(status, "strategic task status"); }
    @Override public String type() { return "frontier.strategic_task_transition"; }
}
