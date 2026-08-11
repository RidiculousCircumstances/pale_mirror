package io.farfrontier.palemirror.internal.presentation.atlas;

/** Cause-first player projection; canonical state remains in Facility/RouteContract. */
public enum SupplyDiagnosis {
    BUILDING,
    OPERATIONAL,
    NO_SOURCE,
    ROUTE_DAMAGED,
    ROUTE_BLOCKED,
    ROUTE_STALE,
    RECOVERING,
    NOT_ESTABLISHED
}
