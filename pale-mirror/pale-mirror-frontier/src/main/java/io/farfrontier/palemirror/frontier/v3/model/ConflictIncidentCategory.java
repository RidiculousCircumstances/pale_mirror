package io.farfrontier.palemirror.frontier.v3.model;

/** Stable top-level classification for a durable, owner-scoped conflict incident. */
public enum ConflictIncidentCategory {
    LAWFUL_LIFECYCLE_LAG,
    PLAYER_WORLD_DISRUPTION,
    RESTART_AMBIGUITY,
    INVARIANT_FAILURE,
    ADAPTER_ERROR;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
