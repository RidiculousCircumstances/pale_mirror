package io.farfrontier.palemirror.frontier.v3.model;

/** Explicit canonical availability of a surveyed edge; unknown never means open. */
public enum TraversalAvailability {
    OPEN,
    BLOCKED,
    DAMAGED,
    UNKNOWN
}
