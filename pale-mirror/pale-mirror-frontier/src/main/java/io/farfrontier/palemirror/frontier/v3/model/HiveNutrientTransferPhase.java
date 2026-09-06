package io.farfrontier.palemirror.frontier.v3.model;

/** Durable phase of one exact nutrient moving through the bounded hive organ network. */
public enum HiveNutrientTransferPhase {
    IN_TRANSIT,
    BLOCKED,
    /** Exact stack remains in the named source slot until its loaded-world departure is observed. */
    DEPARTURE_PENDING,
    /** Exact cargo reached the retained target socket and awaits loaded-world insertion evidence. */
    ARRIVAL_PENDING
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
