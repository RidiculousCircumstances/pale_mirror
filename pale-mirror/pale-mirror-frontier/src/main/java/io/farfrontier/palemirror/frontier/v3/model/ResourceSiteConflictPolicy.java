package io.farfrontier.palemirror.frontier.v3.model;

/** Bounded owner response to a typed field disposition; it never restarts an old harvest. */
public enum ResourceSiteConflictPolicy {
    TERMINAL_REPAIR_REQUIRED,
    RECOVERY_INSPECTION_REQUIRED;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
