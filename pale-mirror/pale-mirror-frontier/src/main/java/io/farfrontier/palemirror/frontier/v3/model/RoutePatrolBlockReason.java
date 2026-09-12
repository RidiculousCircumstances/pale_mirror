package io.farfrontier.palemirror.frontier.v3.model;

/**
 * The patrol owner's bounded, player-readable reason for stopping one exact
 * formation.  This is evidence, not a navigation instruction or a permission
 * to select a replacement route/member.
 */
public enum RoutePatrolBlockReason {
    NO_OPEN_RETAINED_EDGE,
    MISSING_OWNED_BODY,
    CHANGED_OWNED_BODY,
    OCCUPIED_NEXT_BODY,
    DAMAGED_SUPPORT,
    PLAYER_OR_WORLD_OBSTRUCTION,
    RECOVERY_UNRESOLVED
}
