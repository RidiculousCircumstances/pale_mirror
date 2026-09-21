package io.farfrontier.palemirror.frontier.v3.model;

/** The exact physical fact that ended one owned resource-site continuation. */
public enum ResourceSiteConflictReason {
    PLAYER_REMOVED_MANAGED_CELL,
    EXPLOSION_DAMAGED_MANAGED_CELL,
    OBSERVED_MANAGED_CELL_MISMATCH,
    WORKER_DIED,
    RECOVERY_UNRESOLVED,
    /** The exact HOT body was not fenceable at its lawful COLD hand-off. */
    CARRIER_FENCE_UNRESOLVED,
    /** A retained field edge names a column without collision support. */
    FIELD_ROUTE_BLOCKED_SUPPORT,
    /** A retained field edge has a foreign collision body. */
    FIELD_ROUTE_BLOCKED_CLEARANCE,
    /** A retained field edge enters an undeclared fluid medium. */
    FIELD_ROUTE_BLOCKED_MEDIUM,
    /** The observed worker or target is outside the retained pedestrian contract. */
    FIELD_ROUTE_OFF_CONTRACT;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
