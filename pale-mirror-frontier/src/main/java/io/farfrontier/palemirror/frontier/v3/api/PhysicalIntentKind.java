package io.farfrontier.palemirror.frontier.v3.api;

/** Stable semantic category used to select one bounded NeoForge physical executor. */
public enum PhysicalIntentKind {
    CARGO_HANDOFF,
    STRUCTURAL_REPAIR,
    ROUTE_CONSTRUCTION,
    DECONTAMINATION,
    EXPLOSION,
    SCENE_STRIKE,
    /** One exact unit is consumed from one active, identity-tagged physical container stack. */
    EXACT_ITEM_CONSUMPTION
}
