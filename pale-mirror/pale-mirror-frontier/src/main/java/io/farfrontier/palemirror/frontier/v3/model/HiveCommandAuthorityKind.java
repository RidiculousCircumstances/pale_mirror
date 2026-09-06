package io.farfrontier.palemirror.frontier.v3.model;

/** The one admissible command source for an exact hive operation. */
public enum HiveCommandAuthorityKind {
    RELAY,
    OVERSEER;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
