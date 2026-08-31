package io.farfrontier.palemirror.frontier.v3.model;

/** Why an open commercial request could not become an accepted production order. */
enum MarketDemandCancellationReason {
    PRODUCTION_BLOCKED,
    TASK_NO_LONGER_PENDING
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
