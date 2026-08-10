package io.farfrontier.palemirror.domain;

import java.util.OptionalLong;

/** One bounded macro-resource account; physical item inventories never mirror this value. */
public final class ResourceAccount {
    private final int capacity;
    private final int production;
    private final int baseConsumption;
    private final int rationedConsumption;
    private int stock;
    private int incomingFlow;
    private int effectiveConsumption;
    private int actualConsumption;
    private int netFlow;
    private ResourceAvailability availability;

    public ResourceAccount(int capacity, int stock, int production, int baseConsumption, int rationedConsumption) {
        this(capacity, stock, production, baseConsumption, rationedConsumption, 0, baseConsumption, 0, 0,
                ResourceAvailability.AVAILABLE);
    }

    public ResourceAccount(int capacity, int stock, int production, int baseConsumption, int rationedConsumption,
                           int incomingFlow, int effectiveConsumption, int actualConsumption, int netFlow,
                           ResourceAvailability availability) {
        if (capacity < 0 || stock < 0 || stock > capacity || production < 0 || baseConsumption < 0
                || rationedConsumption < 0 || rationedConsumption > baseConsumption || incomingFlow < 0
                || effectiveConsumption < 0 || actualConsumption < 0) {
            throw new IllegalArgumentException("Invalid resource account values");
        }
        this.capacity = capacity;
        this.stock = stock;
        this.production = production;
        this.baseConsumption = baseConsumption;
        this.rationedConsumption = rationedConsumption;
        this.incomingFlow = incomingFlow;
        this.effectiveConsumption = effectiveConsumption;
        this.actualConsumption = actualConsumption;
        this.netFlow = netFlow;
        this.availability = java.util.Objects.requireNonNull(availability, "availability");
    }

    public int capacity() { return capacity; }
    public int stock() { return stock; }
    public int production() { return production; }
    public int incomingFlow() { return incomingFlow; }
    public int baseConsumption() { return baseConsumption; }
    public int rationedConsumption() { return rationedConsumption; }
    public int effectiveConsumption() { return effectiveConsumption; }
    public int actualConsumption() { return actualConsumption; }
    public int netFlow() { return netFlow; }
    public ResourceAvailability availability() { return availability; }
    public OptionalLong reserveSteps() {
        return netFlow < 0 ? OptionalLong.of((stock + (long) -netFlow - 1L) / -netFlow) : OptionalLong.empty();
    }

    void advance(int incoming, boolean rationing) {
        if (incoming < 0) throw new IllegalArgumentException("Incoming flow must not be negative");
        incomingFlow = incoming;
        effectiveConsumption = rationing ? rationedConsumption : baseConsumption;
        int available = Math.min(capacity, stock + production + incomingFlow);
        actualConsumption = Math.min(available, effectiveConsumption);
        stock = available - actualConsumption;
        netFlow = production + incomingFlow - effectiveConsumption;
        availability = actualConsumption < effectiveConsumption ? ResourceAvailability.UNAVAILABLE
                : netFlow < 0 ? ResourceAvailability.STRAINED : ResourceAvailability.AVAILABLE;
    }
}
