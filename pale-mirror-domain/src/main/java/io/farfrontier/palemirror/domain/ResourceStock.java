package io.farfrontier.palemirror.domain;

/** Bounded mutable stock owned by one canonical aggregate. */
public final class ResourceStock {
    private final int capacity;
    private int amount;

    public ResourceStock(int capacity, int amount) {
        if (capacity < 0) throw new IllegalArgumentException("Stock capacity must not be negative");
        if (amount < 0 || amount > capacity) throw new IllegalArgumentException("Stock amount must be within capacity");
        this.capacity = capacity;
        this.amount = amount;
    }

    public int capacity() { return capacity; }
    public int amount() { return amount; }

    public int receive(int incoming) {
        if (incoming < 0) throw new IllegalArgumentException("Incoming resource amount must not be negative");
        int accepted = Math.min(incoming, capacity - amount);
        amount += accepted;
        return accepted;
    }

    public int consume(int requested) {
        if (requested < 0) throw new IllegalArgumentException("Requested resource amount must not be negative");
        int consumed = Math.min(requested, amount);
        amount -= consumed;
        return consumed;
    }
}
