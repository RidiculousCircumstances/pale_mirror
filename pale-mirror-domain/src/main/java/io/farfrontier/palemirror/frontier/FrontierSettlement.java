package io.farfrontier.palemirror.frontier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.EnumMap;
import java.util.Map;

/** Owns people and functional facilities for one settlement. */
public final class FrontierSettlement {
    private final String id;
    private final String name;
    private final FrontierSettlementFocus focus;
    private final FrontierPoint center;
    private final List<String> residentIds = new ArrayList<>();
    private final List<String> facilityIds = new ArrayList<>();
    private final Map<FrontierResource, Long> stock = new EnumMap<>(FrontierResource.class);
    private long netCredit;
    private FrontierCivicState civicState = FrontierCivicState.NORMAL;
    private int threatPermille;
    private int foodReserveDaysMilli;
    private int rationPermille = 1_000;
    private long civicRevision;

    FrontierSettlement(String id, String name, FrontierSettlementFocus focus, FrontierPoint center) {
        this.id = required(id, "id");
        this.name = required(name, "name");
        this.focus = Objects.requireNonNull(focus, "focus");
        this.center = Objects.requireNonNull(center, "center");
        for (FrontierResource resource : FrontierResource.values()) stock.put(resource, 0L);
    }

    public String id() { return id; }
    public String name() { return name; }
    public FrontierSettlementFocus focus() { return focus; }
    public FrontierPoint center() { return center; }
    public Collection<String> residentIds() { return List.copyOf(residentIds); }
    public Collection<String> facilityIds() { return List.copyOf(facilityIds); }
    public long stock(FrontierResource resource) { return stock.get(Objects.requireNonNull(resource, "resource")); }
    public Map<FrontierResource, Long> stocks() { return Map.copyOf(stock); }
    public long netCredit() { return netCredit; }
    public FrontierCivicState civicState() { return civicState; }
    public int threatPermille() { return threatPermille; }
    public int foodReserveDaysMilli() { return foodReserveDaysMilli; }
    public int rationPermille() { return rationPermille; }
    public long civicRevision() { return civicRevision; }
    void addResident(String residentId) { residentIds.add(required(residentId, "residentId")); }
    void addFacility(String facilityId) { facilityIds.add(required(facilityId, "facilityId")); }
    void addStock(FrontierResource resource, long amount) { stock.put(resource, Math.addExact(stock(resource), amount)); }
    boolean removeStock(FrontierResource resource, long amount) {
        if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
        if (stock(resource) < amount) return false;
        stock.put(resource, stock(resource) - amount);
        return true;
    }
    void changeCredit(long amount) { netCredit = Math.addExact(netCredit, amount); }
    void restoreEconomy(Map<FrontierResource, Long> stocks, long netCredit) {
        if (!stocks.keySet().equals(java.util.Set.of(FrontierResource.values()))) {
            throw new IllegalArgumentException("persisted settlement stock kinds do not match profile");
        }
        for (FrontierResource resource : FrontierResource.values()) {
            long amount = stocks.get(resource);
            if (amount < 0) throw new IllegalArgumentException("negative persisted stock");
            stock.put(resource, amount);
        }
        this.netCredit = netCredit;
    }
    boolean reconcileCivic(FrontierCivicState civicState, int threatPermille, int foodReserveDaysMilli,
                           int rationPermille) {
        validateCivic(civicState, threatPermille, foodReserveDaysMilli, rationPermille);
        if (this.civicState == civicState && this.threatPermille == threatPermille
                && this.foodReserveDaysMilli == foodReserveDaysMilli && this.rationPermille == rationPermille) return false;
        this.civicState = civicState;
        this.threatPermille = threatPermille;
        this.foodReserveDaysMilli = foodReserveDaysMilli;
        this.rationPermille = rationPermille;
        civicRevision++;
        return true;
    }
    void restoreCivic(FrontierCivicState civicState, int threatPermille, int foodReserveDaysMilli,
                      int rationPermille, long civicRevision) {
        validateCivic(civicState, threatPermille, foodReserveDaysMilli, rationPermille);
        if (civicRevision < 0) throw new IllegalArgumentException("civic revision must not be negative");
        this.civicState = civicState;
        this.threatPermille = threatPermille;
        this.foodReserveDaysMilli = foodReserveDaysMilli;
        this.rationPermille = rationPermille;
        this.civicRevision = civicRevision;
    }

    private static void validateCivic(FrontierCivicState civicState, int threatPermille, int foodReserveDaysMilli,
                                      int rationPermille) {
        if (civicState == null || threatPermille < 0 || threatPermille > 1_000 || foodReserveDaysMilli < 0
                || rationPermille < 1 || rationPermille > 1_000) {
            throw new IllegalArgumentException("invalid settlement civic state");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
