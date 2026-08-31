package io.farfrontier.palemirror.frontier.v3.model;

/** Durable COLD/HOT lifecycle of one exact hive assault against a named settlement. */
enum SettlementAssaultStatus {
    APPROACHING,
    WAITING_FOR_BATTLE,
    COLD_COMBAT,
    HOT,
    RESOLVED,
    UNKNOWN_AFTER_RESTART,
    CONFLICT
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
