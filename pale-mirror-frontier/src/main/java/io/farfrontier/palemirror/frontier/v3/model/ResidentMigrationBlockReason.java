package io.farfrontier.palemirror.frontier.v3.model;

/** A durable, inspectable reason why an otherwise exact journey cannot advance. */
public enum ResidentMigrationBlockReason {
    QUARANTINE,
    DESTINATION_HOUSING_LOST,
    ROUTE_OBSTRUCTED
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
