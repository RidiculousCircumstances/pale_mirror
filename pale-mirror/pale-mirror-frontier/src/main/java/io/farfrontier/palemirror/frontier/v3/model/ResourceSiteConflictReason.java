package io.farfrontier.palemirror.frontier.v3.model;

/** The exact physical fact that ended one owned resource-site continuation. */
public enum ResourceSiteConflictReason {
    PLAYER_REMOVED_MANAGED_CELL,
    EXPLOSION_DAMAGED_MANAGED_CELL,
    OBSERVED_MANAGED_CELL_MISMATCH,
    WORKER_DIED,
    RECOVERY_UNRESOLVED,
    /** The exact HOT body was not fenceable at its lawful COLD hand-off. */
    CARRIER_FENCE_UNRESOLVED;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
