package io.farfrontier.palemirror.frontier.v3.model;

/** A duty inside one exact route-owned human unit; it is not a profession or a second assignment. */
public enum RouteUnitDuty {
    PATROL_LEADER,
    SCOUT,
    CARGO_CREW,
    ESCORT;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
