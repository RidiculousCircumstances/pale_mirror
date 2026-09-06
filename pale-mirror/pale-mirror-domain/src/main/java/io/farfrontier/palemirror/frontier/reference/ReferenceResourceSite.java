package io.farfrontier.palemirror.frontier.reference;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Physical primary-resource site ported from Python {@code ResourceSite}.
 *
 * <p>Output remains at this site until a later economy haul. The site is not a
 * proxy settlement facility: it carries its own ownership, condition,
 * contamination and finite local store.</p>
 */
public final class ReferenceResourceSite {
    private final int id;
    private final ReferenceSiteKind kind;
    private final int x;
    private final int y;
    private final double quality;
    private double capacity;
    private Integer ownerId;
    private Integer operatorCompanyId;
    private double condition = 1.0d;
    private double contamination;
    private double substrate;
    private final EnumMap<ReferenceResource, Double> stock = emptyStock();
    private double haulCapacity;
    private Integer claimedDay;

    public ReferenceResourceSite(
            int id,
            ReferenceSiteKind kind,
            int x,
            int y,
            double quality,
            double capacity,
            Integer ownerId
    ) {
        this.id = id;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.x = x;
        this.y = y;
        this.quality = quality;
        this.capacity = capacity;
        this.ownerId = ownerId;
    }

    public int id() {
        return id;
    }

    public ReferenceSiteKind kind() {
        return kind;
    }

    public ReferenceResource resource() {
        return kind.resource();
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public double quality() {
        return quality;
    }

    public double capacity() {
        return capacity;
    }

    public void capacity(double value) {
        capacity = value;
    }

    public Integer ownerId() {
        return ownerId;
    }

    public void ownerId(Integer value) {
        ownerId = value;
    }

    public Integer operatorCompanyId() {
        return operatorCompanyId;
    }

    public void operatorCompanyId(Integer value) {
        operatorCompanyId = value;
    }

    public double condition() {
        return condition;
    }

    public void condition(double value) {
        condition = value;
    }

    public double contamination() {
        return contamination;
    }

    public void contamination(double value) {
        contamination = value;
    }

    public double substrate() {
        return substrate;
    }

    public void substrate(double value) {
        substrate = value;
    }

    public double haulCapacity() {
        return haulCapacity;
    }

    public void haulCapacity(double value) {
        haulCapacity = value;
    }

    public Integer claimedDay() {
        return claimedDay;
    }

    public void claimedDay(Integer value) {
        claimedDay = value;
    }

    public boolean operational() {
        return ownerId != null && condition > 0.02d;
    }

    public double distanceTo(int targetX, int targetY) {
        return Math.hypot(x - targetX, y - targetY);
    }

    public double amount(ReferenceResource resource) {
        return stock.get(Objects.requireNonNull(resource, "resource"));
    }

    public void add(ReferenceResource resource, double amount) {
        if (amount > 0.0d) {
            stock.merge(Objects.requireNonNull(resource, "resource"), amount, Double::sum);
        }
    }

    public double remove(ReferenceResource resource, double amount) {
        ReferenceResource required = Objects.requireNonNull(resource, "resource");
        double actual = Math.min(Math.max(0.0d, amount), this.amount(required));
        stock.put(required, this.amount(required) - actual);
        return actual;
    }

    public Map<ReferenceResource, Double> stock() {
        return Map.copyOf(stock);
    }

    private static EnumMap<ReferenceResource, Double> emptyStock() {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        for (ReferenceResource resource : ReferenceResource.values()) {
            result.put(resource, 0.0d);
        }
        return result;
    }
}
