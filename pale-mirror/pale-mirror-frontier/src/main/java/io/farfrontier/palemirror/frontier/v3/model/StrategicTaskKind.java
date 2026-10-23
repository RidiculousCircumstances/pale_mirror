package io.farfrontier.palemirror.frontier.v3.model;

/** Durable work decomposition of one strategic objective. */
public enum StrategicTaskKind {
    DECONTAMINATE_INFECTION_CELL,
    SPREAD_INFECTION_CELL,
    GROW_HIVE_ORGANISM,
    ASSAULT_SETTLEMENT,
    PRODUCE_AT_STATION,
    PATROL_OBSTRUCTED_ROUTE,
    CONSTRUCT_ROUTE_BYPASS,
    HARVEST_RESOURCE_SITE
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
