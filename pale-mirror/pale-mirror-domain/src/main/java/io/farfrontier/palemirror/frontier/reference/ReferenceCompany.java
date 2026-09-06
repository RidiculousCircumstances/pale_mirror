package io.farfrontier.palemirror.frontier.reference;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Mutable private-firm owner ported from Python {@code Company}. */
public final class ReferenceCompany {
    private final int id;
    private final String name;
    private final ReferenceCompanySector sector;
    private final int homeSettlementId;
    private final Set<Integer> siteIds = new LinkedHashSet<>();
    private final Set<String> employeeIds = new LinkedHashSet<>();
    private final EnumMap<ReferenceResource, Double> inventory = emptyInventory();
    private double cash;
    private double capacity;
    private double employees;
    private double wageOffer = 0.42d;
    private double debt;
    private double assets;
    private String ownerKind = "private";
    private String status = "operating";
    private String v2State = "operating";
    private int receiverUntil = -1;
    private int lastInvestmentDay = -10_000;
    private double lastProfit;
    private double wageBill;

    public ReferenceCompany(int id, String name, ReferenceCompanySector sector, int homeSettlementId, double cash, double capacity) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.sector = Objects.requireNonNull(sector, "sector");
        this.homeSettlementId = homeSettlementId;
        this.cash = cash;
        this.capacity = capacity;
    }

    public int id() { return id; }
    public String name() { return name; }
    public ReferenceCompanySector sector() { return sector; }
    public int homeSettlementId() { return homeSettlementId; }
    public ReferenceResource output() { return sector.output(); }
    public boolean critical() { return sector == ReferenceCompanySector.AGRICULTURE || sector == ReferenceCompanySector.ENERGY || sector == ReferenceCompanySector.MEDICINE; }
    public double cash() { return cash; }
    public void cash(double value) { cash = value; }
    public double capacity() { return capacity; }
    public void capacity(double value) { capacity = value; }
    public Set<Integer> siteIds() { return siteIds; }
    public Set<String> employeeIds() { return employeeIds; }
    public double employees() { return employees; }
    public void employees(double value) { employees = value; }
    public double wageOffer() { return wageOffer; }
    public void wageOffer(double value) { wageOffer = value; }
    public double debt() { return debt; }
    public void debt(double value) { debt = value; }
    public double assets() { return assets; }
    public void assets(double value) { assets = value; }
    public String ownerKind() { return ownerKind; }
    public void ownerKind(String value) { ownerKind = Objects.requireNonNull(value, "ownerKind"); }
    public String status() { return status; }
    public void status(String value) { status = Objects.requireNonNull(value, "status"); }
    public String v2State() { return v2State; }
    public void v2State(String value) { v2State = Objects.requireNonNull(value, "v2State"); }
    public int receiverUntil() { return receiverUntil; }
    public void receiverUntil(int value) { receiverUntil = value; }
    public int lastInvestmentDay() { return lastInvestmentDay; }
    public void lastInvestmentDay(int value) { lastInvestmentDay = value; }
    public double lastProfit() { return lastProfit; }
    public void lastProfit(double value) { lastProfit = value; }
    public double wageBill() { return wageBill; }
    public void wageBill(double value) { wageBill = value; }
    public double amount(ReferenceResource resource) { return inventory.get(Objects.requireNonNull(resource, "resource")); }
    public void add(ReferenceResource resource, double amount) { if (amount > 0.0d) inventory.merge(Objects.requireNonNull(resource, "resource"), amount, Double::sum); }
    public double remove(ReferenceResource resource, double amount) {
        ReferenceResource required = Objects.requireNonNull(resource, "resource");
        double actual = Math.min(Math.max(0.0d, amount), inventory.get(required));
        inventory.put(required, inventory.get(required) - actual);
        return actual;
    }
    /** Exact source-policy debit; callers must establish availability before invoking it. */
    void subtract(ReferenceResource resource, double amount) {
        ReferenceResource required = Objects.requireNonNull(resource, "resource");
        inventory.put(required, inventory.get(required) - amount);
    }
    public Map<ReferenceResource, Double> inventory() { return Map.copyOf(inventory); }

    private static EnumMap<ReferenceResource, Double> emptyInventory() {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        for (ReferenceResource resource : ReferenceResource.values()) result.put(resource, 0.0d);
        return result;
    }
}
