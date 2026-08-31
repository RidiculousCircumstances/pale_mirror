package io.farfrontier.palemirror.frontier.v3.model;

/** Stable legal/economic role of one canonical account holder. */
public enum EconomicOwnerKind {
    SETTLEMENT_TREASURY,
    HIVE_COLLECTIVE,
    PUBLIC_INFRASTRUCTURE,
    COMPANY,
    RESIDENT
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
