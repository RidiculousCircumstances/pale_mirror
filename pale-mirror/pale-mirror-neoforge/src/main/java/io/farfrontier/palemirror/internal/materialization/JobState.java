package io.farfrontier.palemirror.internal.materialization;

public enum JobState {
    PLANNED,
    RUNNING,
    BLOCKED,
    COMPLETED,
    FAILED,
    CANCELLED
}
