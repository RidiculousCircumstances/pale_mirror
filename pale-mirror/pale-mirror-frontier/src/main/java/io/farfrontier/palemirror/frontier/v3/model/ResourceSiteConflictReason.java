package io.farfrontier.palemirror.frontier.v3.model;

/** The exact physical fact that ended one owned resource-site continuation. */
public enum ResourceSiteConflictReason {
    PLAYER_REMOVED_MANAGED_CELL,
    EXPLOSION_DAMAGED_MANAGED_CELL,
    OBSERVED_MANAGED_CELL_MISMATCH,
    WORKER_DIED,
    RECOVERY_UNRESOLVED;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
