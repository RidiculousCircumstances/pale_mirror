package io.farfrontier.palemirror.frontier.v3.model;

/** Canonical disease state, deliberately separate from an actor's physical vitality. */
enum ResidentHealthStatus {
    HEALTHY,
    EXPOSED,
    INFECTED,
    RECOVERING
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
