package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Capacity commitment retains physical representation; individual objects never share a stack. */
public record ContainerIncomingStock(Map<String, Long> fungible, Map<String, Long> individual) {
    public ContainerIncomingStock {
        fungible = Map.copyOf(fungible); individual = Map.copyOf(individual);
        requireNonnegative(fungible); requireNonnegative(individual);
    }
    private static final ContainerIncomingStock EMPTY = new ContainerIncomingStock(Map.of(), Map.of());
    private static void requireNonnegative(Map<String, Long> values) {
        for (long quantity : values.values()) if (quantity < 0) throw new IllegalArgumentException("negative incoming stock");
    }
    public static ContainerIncomingStock empty() { return EMPTY; }
    public long individualSlots() {
        long total = 0;
        for (long quantity : individual.values()) total = Math.addExact(total, quantity);
        return total;
    }
    public Map<String, Long> quantities() {
        var total = new HashMap<>(fungible); individual.forEach((kind, quantity) -> total.merge(kind, quantity, Math::addExact));
        return Map.copyOf(total);
    }
    public boolean isEmpty() { return fungible.isEmpty() && individual.isEmpty(); }
}
