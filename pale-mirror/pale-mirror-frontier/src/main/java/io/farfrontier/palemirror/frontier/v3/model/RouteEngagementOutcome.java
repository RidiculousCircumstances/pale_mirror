package io.farfrontier.palemirror.frontier.v3.model;

/** Accounted terminal result of one exact route engagement. */
public enum RouteEngagementOutcome {
    HIVE_VICTORY,
    SETTLEMENT_VICTORY,
    ABORTED
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
