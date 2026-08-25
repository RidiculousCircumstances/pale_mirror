package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Survey receipt that anchors a later licensed-site construction project. */
public final class ReferenceExplorationReport {
    private final int id;
    private final int companyId;
    private final int settlementId;
    private final ReferenceSiteKind kind;
    private final int x;
    private final int y;
    private final double quality;
    private final double confidence;
    private final int discoveredDay;
    private String status;

    public ReferenceExplorationReport(int id, int companyId, int settlementId, ReferenceSiteKind kind, int x, int y,
                                      double quality, double confidence, int discoveredDay, String status) {
        this.id = id;
        this.companyId = companyId;
        this.settlementId = settlementId;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.x = x;
        this.y = y;
        this.quality = quality;
        this.confidence = confidence;
        this.discoveredDay = discoveredDay;
        this.status = Objects.requireNonNull(status, "status");
    }

    public int id() { return id; }
    public int companyId() { return companyId; }
    public int settlementId() { return settlementId; }
    public ReferenceSiteKind kind() { return kind; }
    public int x() { return x; }
    public int y() { return y; }
    public double quality() { return quality; }
    public double confidence() { return confidence; }
    public int discoveredDay() { return discoveredDay; }
    public String status() { return status; }
    public void status(String value) { status = Objects.requireNonNull(value, "status"); }
}
