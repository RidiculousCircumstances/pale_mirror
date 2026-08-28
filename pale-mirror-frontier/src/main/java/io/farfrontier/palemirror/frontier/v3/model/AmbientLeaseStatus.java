package io.farfrontier.palemirror.frontier.v3.model;

/** Durable execution location of one exact non-operation actor. */
public enum AmbientLeaseStatus {
    PREPARED, HOT, DRAINING, CLOSED, UNKNOWN_AFTER_RESTART
}
