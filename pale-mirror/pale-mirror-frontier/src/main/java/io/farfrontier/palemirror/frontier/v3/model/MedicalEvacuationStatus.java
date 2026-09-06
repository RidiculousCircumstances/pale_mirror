package io.farfrontier.palemirror.frontier.v3.model;

/** Durable lifecycle of one exact care operation. */
public enum MedicalEvacuationStatus {
    PREPARED,
    TREATING,
    COMPLETED,
    BLOCKED,
    UNKNOWN_AFTER_RESTART;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
