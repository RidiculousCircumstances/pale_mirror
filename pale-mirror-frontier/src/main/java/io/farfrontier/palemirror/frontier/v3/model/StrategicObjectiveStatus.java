package io.farfrontier.palemirror.frontier.v3.model;

enum StrategicObjectiveStatus {
    ACTIVE,
    BLOCKED,
    COMPLETED
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
