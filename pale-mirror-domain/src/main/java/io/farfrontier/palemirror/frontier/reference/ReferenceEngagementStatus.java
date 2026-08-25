package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code EngagementStatus} lifecycle. */
public enum ReferenceEngagementStatus {
    ACTIVE("active"),
    ATTACKERS_WITHDREW("attackers_withdrew"),
    DEFENDERS_BROKEN("defenders_broken"),
    ATTACKERS_DESTROYED("attackers_destroyed"),
    COMPLETE("complete");

    private final String id;

    ReferenceEngagementStatus(String id) { this.id = id; }
    public String id() { return id; }
}
