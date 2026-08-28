package io.farfrontier.palemirror.frontier.v3.model;

/** Durable COLD/HOT lifecycle of one exact conflict over a route operation. */
enum RouteEngagementStatus {
    APPROACHING,
    READY_FOR_SCENE,
    HOT,
    RESOLVED,
    UNKNOWN_AFTER_RESTART
}
