package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Source V2 emergency purchase receipt, owned by {@link ReferenceV2State}. */
public final class ReferenceProcurementOrder {
    private final int id;
    private final int settlementId;
    private final ReferenceResource resource;
    private final double quantity;
    private final double maxPrice;
    private final int issuedDay;
    private double fulfilled;
    private String status = "open";

    ReferenceProcurementOrder(int id, int settlementId, ReferenceResource resource, double quantity, double maxPrice,
                              int issuedDay, double fulfilled, String status) {
        this.id = id;
        this.settlementId = settlementId;
        this.resource = Objects.requireNonNull(resource, "resource");
        this.quantity = quantity;
        this.maxPrice = maxPrice;
        this.issuedDay = issuedDay;
        this.fulfilled = fulfilled;
        this.status = Objects.requireNonNull(status, "status");
    }

    public int id() { return id; }
    public int settlementId() { return settlementId; }
    public ReferenceResource resource() { return resource; }
    public double quantity() { return quantity; }
    public double maxPrice() { return maxPrice; }
    public int issuedDay() { return issuedDay; }
    public double fulfilled() { return fulfilled; }
    public String status() { return status; }

    void fulfilled(double value) { fulfilled = value; }
    void status(String value) { status = Objects.requireNonNull(value, "status"); }
}
