package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Source V2 delayed claim, identified by its settlement/site/action tuple. */
public final class ReferenceCivicSiteProject {
    private final int settlementId;
    private final int siteId;
    private final String action;
    private int daysRemaining;

    ReferenceCivicSiteProject(int settlementId, int siteId, String action, int daysRemaining) {
        this.settlementId = settlementId;
        this.siteId = siteId;
        this.action = Objects.requireNonNull(action, "action");
        this.daysRemaining = daysRemaining;
    }

    public int settlementId() { return settlementId; }
    public int siteId() { return siteId; }
    public String action() { return action; }
    public int daysRemaining() { return daysRemaining; }
    void daysRemaining(int value) { daysRemaining = value; }
}
