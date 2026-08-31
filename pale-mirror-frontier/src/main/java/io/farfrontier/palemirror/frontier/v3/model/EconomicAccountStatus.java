package io.farfrontier.palemirror.frontier.v3.model;

/** An insolvent account may retain claims but cannot originate a new debit. */
public enum EconomicAccountStatus {
    ACTIVE,
    INSOLVENT
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
