package io.farfrontier.palemirror.frontier.v3.model;

/** Player-visible class of a failed physical cocoon-release boundary. */
public enum HiveMobilizationConflictReason {
    /** The retained cocoon claim or its loaded block no longer matches the exact occupant. */
    COCOON_CHANGED,
    /** A server stop split durable release admission from an unconfirmed world effect. */
    UNKNOWN_AFTER_RESTART,
    /** A naturally loaded retained assembly target is physically obstructed; no hidden sidestep exists. */
    ASSEMBLY_PATH_BLOCKED;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
