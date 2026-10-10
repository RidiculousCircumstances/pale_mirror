package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Immutable storage packing facts, not reservations or another stock authority. */
record ContainerSlotBudget(Set<Integer> occupied, Set<Integer> bound, long packedStacks, Map<String, Long> stockByKind) {
    ContainerSlotBudget {
        occupied = Set.copyOf(occupied); bound = Set.copyOf(bound); stockByKind = Map.copyOf(stockByKind);
    }
    long requiredSlots() { return occupied.size() + Math.max(packedStacks, bound.size()); }
    ContainerSlotBudget withIncoming(Map<String, Long> incoming) {
        if (incoming.isEmpty()) return this;
        Map<String, Long> projected = new HashMap<>(stockByKind);
        incoming.forEach((kind, quantity) -> projected.merge(kind, quantity, Math::addExact));
        long packed = projected.values().stream().mapToLong(quantity -> (quantity + 63) / 64).sum();
        return new ContainerSlotBudget(occupied, bound, packed, projected);
    }
}
