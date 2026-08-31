package io.farfrontier.palemirror.frontier.v3.model;

public enum StrategicTaskStatus {
    PENDING,
    ACTIVE,
    BLOCKED,
    COMPLETED
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
