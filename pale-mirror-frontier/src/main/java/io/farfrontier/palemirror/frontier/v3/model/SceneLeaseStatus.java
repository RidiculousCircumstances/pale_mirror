package io.farfrontier.palemirror.frontier.v3.model;

/** Durable lifecycle of an exclusive execution-location hand-off. */
public enum SceneLeaseStatus { PREPARED, HOT, DRAINING, CLOSED, UNKNOWN_AFTER_RESTART }
