package io.farfrontier.palemirror.frontier.v3.model;

/** Canonical lifecycle of one renewable site; physical work is retained separately from its stage. */
public enum ResourceSitePhase {
    UNPREPARED,
    GROWING,
    READY,
    HARVESTING,
    CONFLICT,
    DESTROYED
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
