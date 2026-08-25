package io.farfrontier.palemirror.frontier.reference;

import java.util.List;
import java.util.Objects;

/** Durable company-to-settlement supply contract from Python {@code Contract}. */
public final class ReferenceContract {
    private final int id;
    private final int sellerCompanyId;
    private final int buyerSettlementId;
    private final ReferenceResource resource;
    private final double dailyQuantity;
    private final double priceIndex;
    private final int startDay;
    private final int endDay;
    private final List<Integer> route;
    private String status = "active";
    private double delivered;
    private double breachedQuantity;

    public ReferenceContract(int id, int sellerCompanyId, int buyerSettlementId, ReferenceResource resource, double dailyQuantity,
                             double priceIndex, int startDay, int endDay, List<Integer> route) {
        this.id = id;
        this.sellerCompanyId = sellerCompanyId;
        this.buyerSettlementId = buyerSettlementId;
        this.resource = Objects.requireNonNull(resource, "resource");
        this.dailyQuantity = dailyQuantity;
        this.priceIndex = priceIndex;
        this.startDay = startDay;
        this.endDay = endDay;
        this.route = List.copyOf(Objects.requireNonNull(route, "route"));
    }

    public int id() { return id; }
    public int sellerCompanyId() { return sellerCompanyId; }
    public int buyerSettlementId() { return buyerSettlementId; }
    public ReferenceResource resource() { return resource; }
    public double dailyQuantity() { return dailyQuantity; }
    public double priceIndex() { return priceIndex; }
    public int startDay() { return startDay; }
    public int endDay() { return endDay; }
    public List<Integer> route() { return route; }
    public String status() { return status; }
    public void status(String value) { status = Objects.requireNonNull(value, "status"); }
    public double delivered() { return delivered; }
    public void delivered(double value) { delivered = value; }
    public double breachedQuantity() { return breachedQuantity; }
    public void breachedQuantity(double value) { breachedQuantity = value; }
}
