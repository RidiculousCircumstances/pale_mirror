package io.farfrontier.palemirror.domain;

/** Player-facing knowledge; this never controls whether the physical object exists. */
public enum KnownRegionalFeature {
    SETTLEMENT,
    DEPOT,
    PRIMARY_ROUTE,
    PRIMARY_MINE,
    ALTERNATE_SOURCE,
    REFUGEE_SITE
}
