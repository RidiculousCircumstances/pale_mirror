package io.farfrontier.palemirror.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Canonical settlement economy and resilience; no Minecraft entity is authoritative here. */
public final class SettlementState {
    private static final int SHORTAGE_DEFENCE_LOSS_PER_STEP = 8;
    private static final int DECLINING_AFTER_SHORTAGE_STEPS = 12;

    private final WorldObjectId id;
    private final Map<ResourceKind, ResourceStock> stocks;
    private final Map<ResourceKind, Integer> consumption;
    private final int baseDefense;
    private int population;
    private int currentDefense;
    private int shortageSteps;
    private SettlementStatus status;

    public SettlementState(WorldObjectId id, int population, int baseDefense,
                           Map<ResourceKind, ResourceStock> stocks, Map<ResourceKind, Integer> consumption) {
        this(id, population, baseDefense, stocks, consumption, baseDefense, 0, SettlementStatus.STABLE);
    }

    public SettlementState(WorldObjectId id, int population, int baseDefense,
                           Map<ResourceKind, ResourceStock> stocks, Map<ResourceKind, Integer> consumption,
                           int currentDefense, int shortageSteps, SettlementStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        if (population < 0) throw new IllegalArgumentException("Settlement population must not be negative");
        if (baseDefense < 0 || baseDefense > 100 || currentDefense < 0 || currentDefense > 100) {
            throw new IllegalArgumentException("Defence must be within 0..100");
        }
        if (shortageSteps < 0) throw new IllegalArgumentException("Shortage steps must not be negative");
        this.population = population;
        this.baseDefense = baseDefense;
        this.currentDefense = currentDefense;
        this.shortageSteps = shortageSteps;
        this.status = Objects.requireNonNull(status, "status");
        this.stocks = copyStocks(stocks);
        this.consumption = copyConsumption(consumption);
    }

    public WorldObjectId id() { return id; }
    public int population() { return population; }
    public int baseDefense() { return baseDefense; }
    public int currentDefense() { return currentDefense; }
    public int shortageSteps() { return shortageSteps; }
    public SettlementStatus status() { return status; }
    public int stock(ResourceKind resource) { return requireStock(resource).amount(); }
    public int stockCapacity(ResourceKind resource) { return requireStock(resource).capacity(); }
    public int consumption(ResourceKind resource) { return consumption.getOrDefault(resource, 0); }
    public Map<ResourceKind, Integer> consumption() { return Map.copyOf(consumption); }
    public Map<ResourceKind, ResourceStock> stocks() { return Map.copyOf(stocks); }
    public boolean supplyDisrupted() { return status == SettlementStatus.SHORTAGE || status == SettlementStatus.DECLINING; }

    /** Applies all deliveries and consumption for exactly one deterministic simulation step. */
    public void advanceResources(Map<ResourceKind, Integer> delivered) {
        if (status == SettlementStatus.ABANDONED) return;
        for (ResourceKind resource : ResourceKind.values()) {
            ResourceStock stock = stocks.get(resource);
            if (stock == null) continue;
            int incoming = delivered.getOrDefault(resource, 0);
            if (incoming < 0) throw new IllegalArgumentException("Delivered resource amount must not be negative");
            stock.receive(incoming);
        }
        boolean ironShortage = false;
        for (Map.Entry<ResourceKind, Integer> need : consumption.entrySet()) {
            int consumed = requireStock(need.getKey()).consume(need.getValue());
            if (need.getKey() == ResourceKind.IRON && consumed < need.getValue()) ironShortage = true;
        }
        if (!ironShortage) {
            shortageSteps = 0;
            if (status != SettlementStatus.DECLINING) {
                status = SettlementStatus.STABLE;
                currentDefense = baseDefense;
            }
            return;
        }
        shortageSteps++;
        currentDefense = Math.max(0, baseDefense - SHORTAGE_DEFENCE_LOSS_PER_STEP * shortageSteps);
        status = shortageSteps >= DECLINING_AFTER_SHORTAGE_STEPS ? SettlementStatus.DECLINING : SettlementStatus.SHORTAGE;
    }

    public boolean evacuate(int requestedPopulation) {
        if (requestedPopulation <= 0 || requestedPopulation > population || status == SettlementStatus.ABANDONED) return false;
        population -= requestedPopulation;
        currentDefense = Math.min(currentDefense, Math.max(0, baseDefense - 25));
        status = population == 0 ? SettlementStatus.ABANDONED : SettlementStatus.DECLINING;
        return true;
    }

    private ResourceStock requireStock(ResourceKind resource) {
        ResourceStock stock = stocks.get(resource);
        if (stock == null) throw new IllegalStateException("Settlement " + id + " lacks stock for " + resource);
        return stock;
    }

    private static Map<ResourceKind, ResourceStock> copyStocks(Map<ResourceKind, ResourceStock> value) {
        Objects.requireNonNull(value, "stocks");
        Map<ResourceKind, ResourceStock> copied = new EnumMap<>(ResourceKind.class);
        value.forEach((resource, stock) -> copied.put(Objects.requireNonNull(resource, "resource"),
                new ResourceStock(Objects.requireNonNull(stock, "stock").capacity(), stock.amount())));
        return copied;
    }

    private static Map<ResourceKind, Integer> copyConsumption(Map<ResourceKind, Integer> value) {
        Objects.requireNonNull(value, "consumption");
        Map<ResourceKind, Integer> copied = new EnumMap<>(ResourceKind.class);
        value.forEach((resource, amount) -> {
            if (amount == null || amount < 0) throw new IllegalArgumentException("Consumption must not be negative");
            copied.put(Objects.requireNonNull(resource, "resource"), amount);
        });
        return copied;
    }
}
