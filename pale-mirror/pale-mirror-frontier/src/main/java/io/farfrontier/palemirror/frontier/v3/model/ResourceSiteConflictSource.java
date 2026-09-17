package io.farfrontier.palemirror.frontier.v3.model;

/** Registered producer source; prevents a generic mismatch from obscuring its owning boundary. */
public enum ResourceSiteConflictSource {
    LAWFUL_LIFECYCLE_LAG(ConflictIncidentCategory.LAWFUL_LIFECYCLE_LAG),
    PLAYER_WORLD_OBSERVATION(ConflictIncidentCategory.PLAYER_WORLD_DISRUPTION),
    EXPLOSION_WITNESS(ConflictIncidentCategory.PLAYER_WORLD_DISRUPTION),
    RESTART_RECONCILIATION(ConflictIncidentCategory.RESTART_AMBIGUITY),
    PHYSICAL_INTENT_RECOVERY(ConflictIncidentCategory.RESTART_AMBIGUITY),
    LIFECYCLE_RECONCILIATION(ConflictIncidentCategory.INVARIANT_FAILURE),
    WORKER_DEATH(ConflictIncidentCategory.PLAYER_WORLD_DISRUPTION),
    ADAPTER_WRITE_FAILURE(ConflictIncidentCategory.ADAPTER_ERROR),
    /** A lawful HOT-to-COLD release could not preserve the exact worker carrier. */
    SCENE_CARRIER_FENCE(ConflictIncidentCategory.LAWFUL_LIFECYCLE_LAG);

    private final ConflictIncidentCategory category;
    ResourceSiteConflictSource(ConflictIncidentCategory category) { this.category = category; }
    public ConflictIncidentCategory category() { return category; }
    public int wireTag() { return FrontierWireTags.tag(this); }
}
