package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Bounded in-progress physical-site construction from Python's project record. */
public final class ReferenceConstructionProject {
    private final int id;
    private final int companyId;
    private final int reportId;
    private int daysRemaining;
    private String status = "building";

    public ReferenceConstructionProject(int id, int companyId, int reportId, int daysRemaining) {
        this.id = id;
        this.companyId = companyId;
        this.reportId = reportId;
        this.daysRemaining = daysRemaining;
    }

    public int id() { return id; }
    public int companyId() { return companyId; }
    public int reportId() { return reportId; }
    public int daysRemaining() { return daysRemaining; }
    public void daysRemaining(int value) { daysRemaining = value; }
    public String status() { return status; }
    public void status(String value) { status = Objects.requireNonNull(value, "status"); }
}
