package io.farfrontier.palemirror.frontier.v3.model;

/** Functional identity of one stable organ in the distributed hive. */
public enum HiveOrganKind {
    GANGLION,
    RELAY,
    STORE,
    DIGESTER,
    BROOD,
    HIBERNACULUM,
    MORPHER,
    SPORULATOR,
    SENSOR
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
