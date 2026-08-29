package io.farfrontier.palemirror.frontier.v3.api;

/** Exact fact an executor must inspect after a physical intent or after restart. */
public enum PhysicalPostcondition {
    CARGO_HANDOFF_OBSERVED,
    STRUCTURAL_REPAIR_OBSERVED,
    ROUTE_CONSTRUCTION_OBSERVED,
    DECONTAMINATION_OBSERVED,
    EXPLOSION_OBSERVED,
    SCENE_STRIKE_OBSERVED,
    EXACT_ITEM_CONSUMED_OBSERVED,
    RESOURCE_SITE_PREPARED_OBSERVED,
    RESOURCE_SITE_HARVESTED_OBSERVED
}
