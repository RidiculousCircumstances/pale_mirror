package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Mutable source port of Python {@code FieldLink}; its field owner advances construction. */
public final class ReferenceFieldLink {
    private final int id;
    private final ReferenceFieldLinkKind kind;
    private final int aPostId;
    private final int bPostId;
    private final int campaignId;
    private double integrity;
    private String status = "building";
    private int buildDaysRemaining;

    ReferenceFieldLink(int id, ReferenceFieldLinkKind kind, int aPostId, int bPostId, int campaignId) {
        this.id = id;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.aPostId = aPostId;
        this.bPostId = bPostId;
        this.campaignId = campaignId;
        integrity = ReferenceFieldRules.linkIntegrity(kind);
        buildDaysRemaining = ReferenceFieldRules.linkBuildDays(kind);
    }

    public int id() { return id; }
    public ReferenceFieldLinkKind kind() { return kind; }
    public int aPostId() { return aPostId; }
    public int bPostId() { return bPostId; }
    public int campaignId() { return campaignId; }
    public double integrity() { return integrity; }
    void integrity(double value) { integrity = Math.max(0.0d, value); }
    public String status() { return status; }
    void status(String value) { status = Objects.requireNonNull(value, "status"); }
    public int buildDaysRemaining() { return buildDaysRemaining; }
    void buildDaysRemaining(int value) { buildDaysRemaining = value; }
}
