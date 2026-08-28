package io.farfrontier.palemirror.frontier.v3.model;

/** Durable COLD/HOT lifecycle of one exact conflict over a route operation. */
enum RouteEngagementStatus {
    APPROACHING,
    WAITING_FOR_INTERCEPT,
    COLD_COMBAT,
    HOT,
    RESOLVED,
    UNKNOWN_AFTER_RESTART
}
