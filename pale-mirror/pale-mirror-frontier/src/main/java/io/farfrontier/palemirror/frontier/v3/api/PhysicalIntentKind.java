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
    ROUTE_CONSTRUCTION_MATERIAL_LOADING,
    /** One exact active hive STORE stack leaves its owned chest before entering the organ network. */
    HIVE_NUTRIENT_DEPARTURE,
    /** One exact in-transit hive nutrient enters its named active STORE slot. */
    HIVE_NUTRIENT_ARRIVAL,
    /** One exact settlement-owned equipment stack moves into one named active defender's hand. */
    EQUIPMENT_ISSUE,
    /** One exact former defender's hand stack moves into its named free active-depot slot. */
    EQUIPMENT_RETURN,
    /** One exact cargo unit restores one observed retained route surface or foundation cell. */
    ROUTE_MAINTENANCE,
    /** One exact maintenance-depot stack becomes cargo for one retained-route repair. */
    ROUTE_MAINTENANCE_MATERIAL_LOADING,
    /** One exact depot stack visibly crosses to one named settlement service worker. */
    SETTLEMENT_SERVICE_INPUT_ISSUE;

    public int wireTag() {
        return switch (this) {
            case CARGO_HANDOFF -> 0;
            case STRUCTURAL_REPAIR -> 1;
            case ROUTE_CONSTRUCTION -> 2;
            case DECONTAMINATION -> 3;
            case EXPLOSION -> 4;
            case SCENE_STRIKE -> 5;
            case EXACT_ITEM_CONSUMPTION -> 6;
            case RESOURCE_SITE_PREPARATION -> 7;
            case RESOURCE_SITE_HARVEST -> 8;
            case PRODUCTION_TRANSFORMATION -> 9;
            case CARGO_LOADING -> 10;
            case ROUTE_CONSTRUCTION_MATERIAL_LOADING -> 11;
            case HIVE_NUTRIENT_DEPARTURE -> 12;
            case HIVE_NUTRIENT_ARRIVAL -> 13;
            case EQUIPMENT_ISSUE -> 14;
            case EQUIPMENT_RETURN -> 15;
            case ROUTE_MAINTENANCE -> 16;
            case ROUTE_MAINTENANCE_MATERIAL_LOADING -> 17;
            case SETTLEMENT_SERVICE_INPUT_ISSUE -> 18;
        };
    }
}
