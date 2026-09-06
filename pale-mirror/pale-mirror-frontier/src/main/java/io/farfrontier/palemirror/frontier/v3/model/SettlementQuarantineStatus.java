package io.farfrontier.palemirror.frontier.v3.model;

/** Durable settlement policy imposed by known local infection or ill residents. */
public enum SettlementQuarantineStatus {
    NORMAL,
    QUARANTINED
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
