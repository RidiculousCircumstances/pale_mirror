package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Physical-site operating licence from Python {@code Licence}. */
public final class ReferenceLicence {
    private final int id;
    private final int settlementId;
    private final int companyId;
    private final ReferenceCompanySector sector;
    private final Integer siteId;
    private final double rentPerDay;
    private final int issuedDay;
    private String status = "active";

    public ReferenceLicence(int id, int settlementId, int companyId, ReferenceCompanySector sector, Integer siteId, double rentPerDay, int issuedDay) {
        this.id = id;
        this.settlementId = settlementId;
        this.companyId = companyId;
        this.sector = Objects.requireNonNull(sector, "sector");
        this.siteId = siteId;
        this.rentPerDay = rentPerDay;
        this.issuedDay = issuedDay;
    }

    public int id() { return id; }
    public int settlementId() { return settlementId; }
    public int companyId() { return companyId; }
    public ReferenceCompanySector sector() { return sector; }
    public Integer siteId() { return siteId; }
    public double rentPerDay() { return rentPerDay; }
    public int issuedDay() { return issuedDay; }
    public String status() { return status; }
    public void status(String value) { status = Objects.requireNonNull(value, "status"); }
}
