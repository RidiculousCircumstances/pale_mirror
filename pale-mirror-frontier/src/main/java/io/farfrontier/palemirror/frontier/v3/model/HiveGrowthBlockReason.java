package io.farfrontier.palemirror.frontier.v3.model;

/** Why one explicitly scheduled hive-growth attempt could not begin. */
public enum HiveGrowthBlockReason {
    BIOMASS_UNAVAILABLE,
    GROWTH_CAPACITY_UNAVAILABLE,
    PHYSICAL_CONSUMPTION_UNKNOWN
}
