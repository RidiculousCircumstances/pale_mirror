package io.farfrontier.palemirror.frontier.v3.model;

/** Durable lifecycle of one exact patrol column. */
public enum RoutePatrolStatus {
    ASSEMBLING,
    EN_ROUTE,
    ROUTE_CLEAR,
    OBSTRUCTION_CONFIRMED,
    BLOCKED,
    FAILED
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
