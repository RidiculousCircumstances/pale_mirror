package io.farfrontier.palemirror.frontier.v3.model;

/** Terminal evidence retained for a guard's bounded COLD route patrol. */
public enum RoutePatrolStatus {
    EN_ROUTE,
    ROUTE_CLEAR,
    OBSTRUCTION_CONFIRMED,
    FAILED
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
