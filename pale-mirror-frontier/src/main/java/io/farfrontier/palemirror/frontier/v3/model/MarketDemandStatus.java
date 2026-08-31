package io.farfrontier.palemirror.frontier.v3.model;

/** Lifecycle of one buyer need; terminal status never authorizes a new order for that identity. */
public enum MarketDemandStatus {
    OPEN,
    ORDERED,
    FULFILLED,
    CANCELLED,
    EXPIRED
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
