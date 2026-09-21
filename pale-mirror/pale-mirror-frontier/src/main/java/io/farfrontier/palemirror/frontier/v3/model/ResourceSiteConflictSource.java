package io.farfrontier.palemirror.frontier.v3.model;

/** Registered producer source; prevents a generic mismatch from obscuring its owning boundary. */
public enum ResourceSiteConflictSource {
    LAWFUL_LIFECYCLE_LAG,
    PLAYER_WORLD_OBSERVATION,
    EXPLOSION_WITNESS,
    RESTART_RECONCILIATION,
    PHYSICAL_INTENT_RECOVERY,
    LIFECYCLE_RECONCILIATION,
    WORKER_DEATH,
    ADAPTER_WRITE_FAILURE,
    /** A lawful HOT-to-COLD release could not preserve the exact worker carrier. */
    SCENE_CARRIER_FENCE,
    /** The shared semantic movement provider rejected one retained field edge. */
    SCENE_TRAVERSAL;
    public int wireTag() { return FrontierWireTags.tag(this); }
}
