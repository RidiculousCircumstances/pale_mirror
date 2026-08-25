package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code EngagementKind} vocabulary owned by field warfare. */
public enum ReferenceEngagementKind {
    POST_DEFENCE("post_defence"),
    NEST_RAID("nest_raid");

    private final String id;

    ReferenceEngagementKind(String id) { this.id = id; }
    public String id() { return id; }
}
