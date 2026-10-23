package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Capacity commitment retains physical representation; individual objects never share a stack. */
public record ContainerIncomingStock(Map<String, Long> fungible, Map<String, Long> individual) {
    public ContainerIncomingStock {
        fungible = Map.copyOf(fungible); individual = Map.copyOf(individual);
        if (fungible.values().stream().anyMatch(n -> n < 0) || individual.values().stream().anyMatch(n -> n < 0))
            throw new IllegalArgumentException("negative incoming stock");
    }
    public static ContainerIncomingStock empty() { return new ContainerIncomingStock(Map.of(), Map.of()); }
    public long individualSlots() { return individual.values().stream().mapToLong(Long::longValue).reduce(0, Math::addExact); }
    public Map<String, Long> quantities() {
        var total = new HashMap<>(fungible); individual.forEach((kind, quantity) -> total.merge(kind, quantity, Math::addExact));
        return Map.copyOf(total);
    }
    public boolean isEmpty() { return fungible.isEmpty() && individual.isEmpty(); }
}
