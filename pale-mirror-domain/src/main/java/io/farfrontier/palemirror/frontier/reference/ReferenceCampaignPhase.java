package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code CampaignPhase} lifecycle. */
public enum ReferenceCampaignPhase {
    ASSESSMENT("assessment"),
    ESTABLISH("establish"),
    BUILD_UP("build_up"),
    ENGAGE("engage"),
    CONSOLIDATE("consolidate"),
    WITHDRAW("withdraw"),
    COMPLETE("complete"),
    FAILED("failed");

    private final String id;

    ReferenceCampaignPhase(String id) { this.id = id; }
    public String id() { return id; }
}
