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
    EXACT_ITEM_CONSUMPTION,
    /** Loaded-chunk preparation of the fixed soil and crop cells of one named renewable site. */
    RESOURCE_SITE_PREPARATION,
    /** Loaded-chunk exact harvest of one mature renewable site into one named output stack. */
    RESOURCE_SITE_HARVEST,
    /** One exact owned input stack becomes one named exact output stack in its physical slot. */
    PRODUCTION_TRANSFORMATION,
    /** One exact active-depot stack leaves its physical slot before becoming a named cargo batch. */
    CARGO_LOADING,
    /** One exact maintenance stack leaves its owned chest before becoming a COLD route-work cargo. */
    ROUTE_CONSTRUCTION_MATERIAL_LOADING
}
