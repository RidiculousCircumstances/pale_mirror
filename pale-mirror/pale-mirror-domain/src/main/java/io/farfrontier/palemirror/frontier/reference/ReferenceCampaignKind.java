package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code CampaignKind} vocabulary. */
public enum ReferenceCampaignKind {
    CONTAINMENT("containment"),
    OFFENSIVE("offensive"),
    RELIEF("relief");

    private final String id;

    ReferenceCampaignKind(String id) { this.id = id; }
    public String id() { return id; }
}
