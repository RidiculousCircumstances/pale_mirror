package io.farfrontier.palemirror.frontier.v3.model;

/** Lifecycle of the one accepted offer for a market demand. */
public enum MarketWorkOrderStatus {
    ACCEPTED,
    FULFILLED,
    CANCELLED,
    CONFLICT
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
