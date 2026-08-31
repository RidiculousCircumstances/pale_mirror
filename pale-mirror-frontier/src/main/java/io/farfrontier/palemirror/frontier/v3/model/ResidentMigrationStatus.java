package io.farfrontier.palemirror.frontier.v3.model;

/** Explicit lifecycle for one exact resident's COLD migration journey. */
public enum ResidentMigrationStatus {
    EN_ROUTE,
    BLOCKED
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
