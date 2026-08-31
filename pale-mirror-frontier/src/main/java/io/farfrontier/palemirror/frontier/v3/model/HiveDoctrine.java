package io.farfrontier.palemirror.frontier.v3.model;

/** The hive's one current strategic posture; it is not a human-settlement policy clone. */
enum HiveDoctrine {
    CONSOLIDATE,
    EXPAND,
    INTERDICT
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
