package io.farfrontier.palemirror.domain;

import java.util.OptionalLong;

/** One bounded macro-resource account; physical item inventories never mirror this value. */
public final class ResourceAccount {
    private int capacity;
    private final int production;
    private final int baseConsumption;
    private final int rationedConsumption;
    private int stock;
    private int incomingFlow;
    private int effectiveConsumption;
    private int actualConsumption;
    private int netFlow;
    private ResourceAvailability availability;
    private int reserved;

    public ResourceAccount(int capacity, int stock, int production, int baseConsumption, int rationedConsumption) {
        this(capacity, stock, production, baseConsumption, rationedConsumption, 0, baseConsumption, 0, 0,
                ResourceAvailability.AVAILABLE, 0);
    }

    public ResourceAccount(int capacity, int stock, int production, int baseConsumption, int rationedConsumption,
                           int incomingFlow, int effectiveConsumption, int actualConsumption, int netFlow,
                           ResourceAvailability availability) {
        this(capacity, stock, production, baseConsumption, rationedConsumption, incomingFlow, effectiveConsumption,
                actualConsumption, netFlow, availability, 0);
    }

    public ResourceAccount(int capacity, int stock, int production, int baseConsumption, int rationedConsumption,
                           int incomingFlow, int effectiveConsumption, int actualConsumption, int netFlow,
                           ResourceAvailability availability, int reserved) {
        if (capacity < 0 || stock < 0 || stock > capacity || production < 0 || baseConsumption < 0
                || rationedConsumption < 0 || rationedConsumption > baseConsumption || incomingFlow < 0
                || effectiveConsumption < 0 || actualConsumption < 0 || reserved < 0 || reserved > stock) {
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
        this.reserved = reserved;
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
    public int reserved() { return reserved; }
    public OptionalLong reserveSteps() {
        return netFlow < 0 ? OptionalLong.of((stock + (long) -netFlow - 1L) / -netFlow) : OptionalLong.empty();
    }

    /** Credits an external, explicitly receipted delivery and returns the accepted amount. */
    public int credit(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Credit must not be negative");
        if (amount > capacity - stock) return 0;
        stock += amount;
        return amount;
    }

    /** Debits canonical stock only when the complete amount is available. */
    public boolean debit(int amount, int minimumRemaining) {
        if (amount < 0 || minimumRemaining < 0) throw new IllegalArgumentException("Debit values must not be negative");
        if (stock - reserved - amount < minimumRemaining) return false;
        stock -= amount;
        return true;
    }

    public void expandCapacity(int newCapacity) {
        if (newCapacity < capacity) throw new IllegalArgumentException("Resource capacity cannot shrink");
        capacity = newCapacity;
    }

    public boolean reserve(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Reservation must not be negative");
        if (stock - reserved < amount) return false;
        reserved += amount;
        return true;
    }

    public void consumeReservation(int amount) {
        if (amount < 0 || amount > reserved) throw new IllegalArgumentException("Invalid reservation consumption");
        reserved -= amount;
        stock -= amount;
    }

    public void releaseReservation(int amount) {
        if (amount < 0 || amount > reserved) throw new IllegalArgumentException("Invalid reservation release");
        reserved -= amount;
    }

    void advance(int incoming, boolean rationing) {
        if (incoming < 0) throw new IllegalArgumentException("Incoming flow must not be negative");
        incomingFlow = incoming;
        effectiveConsumption = rationing ? rationedConsumption : baseConsumption;
        int available = Math.min(capacity, stock + production + incomingFlow);
        actualConsumption = Math.min(Math.max(0, available - reserved), effectiveConsumption);
        stock = available - actualConsumption;
        netFlow = production + incomingFlow - effectiveConsumption;
        availability = actualConsumption < effectiveConsumption ? ResourceAvailability.UNAVAILABLE
                : netFlow < 0 ? ResourceAvailability.STRAINED : ResourceAvailability.AVAILABLE;
    }
}
