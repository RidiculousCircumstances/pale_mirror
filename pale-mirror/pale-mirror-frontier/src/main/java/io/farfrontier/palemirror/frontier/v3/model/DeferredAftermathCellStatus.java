package io.farfrontier.palemirror.frontier.v3.model;

/** Local durable resolution of one declared aftermath cell. */
public enum DeferredAftermathCellStatus {
    PENDING,
    RUNNING,
    REALIZED,
    CONFLICTED
}
