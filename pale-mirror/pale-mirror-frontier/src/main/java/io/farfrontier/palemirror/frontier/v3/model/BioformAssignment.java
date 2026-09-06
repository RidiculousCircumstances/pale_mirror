package io.farfrontier.palemirror.frontier.v3.model;

/** The one current purpose selected for an exact hive organism. */
public enum BioformAssignment {
    WATCH,
    SCOUT,
    HARVEST,
    CARRY,
    REPAIR,
    DEFEND,
    ASSAULT,
    SIEGE,
    RETREAT;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
