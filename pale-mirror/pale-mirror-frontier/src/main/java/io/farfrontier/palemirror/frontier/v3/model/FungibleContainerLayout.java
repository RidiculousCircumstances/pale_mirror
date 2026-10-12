package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Last confirmed placement, not stock, ownership, reservations or permission to write a container. */
public record FungibleContainerLayout(Map<Integer, Stack> slots) {
    private static final java.util.regex.Pattern ITEM_KIND = java.util.regex.Pattern.compile("[a-z0-9_]+:[a-z0-9_./-]+");
    public static final class CapacityUnavailable extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private CapacityUnavailable() { super("current stock exceeds available container slots"); }
    }
    public record Stack(String itemKind, int quantity) {
        public Stack {
            if (itemKind == null || itemKind.length() > 256 || !ITEM_KIND.matcher(itemKind).matches()
                    || quantity < 1 || quantity > 64)
                throw new IllegalArgumentException("invalid retained container placement");
        }
    }
    public FungibleContainerLayout {
        slots = Map.copyOf(slots);
        if (slots.size() > 54 || slots.keySet().stream().anyMatch(slot -> slot < 0 || slot >= 54))
            throw new IllegalArgumentException("retained container placement exceeds its bounded slot grammar");
    }
    public static FungibleContainerLayout empty() { return new FungibleContainerLayout(Map.of()); }

    /** Preserve unaffected positions; only changed quantities and genuinely new stock need allocation. */
    public Map<Integer, Stack> project(Map<String, Integer> quantities, int capacity, Set<Integer> unavailable) {
        if (capacity < 1 || capacity > 54 || quantities.values().stream().anyMatch(quantity -> quantity < 1))
            throw new IllegalArgumentException("invalid current container projection input");
        var remaining = new TreeMap<>(quantities);
        var result = new TreeMap<Integer, Stack>();
        for (var entry : new TreeMap<>(slots).entrySet()) {
            int slot = entry.getKey(); var prior = entry.getValue();
            if (slot >= capacity || unavailable.contains(slot)) continue;
            int keep = Math.min(prior.quantity(), remaining.getOrDefault(prior.itemKind(), 0));
            if (keep == 0) continue;
            result.put(slot, new Stack(prior.itemKind(), keep));
            remaining.put(prior.itemKind(), remaining.get(prior.itemKind()) - keep);
        }
        // New arrivals can top up a compatible stack without moving any existing one.
        for (var entry : new ArrayList<>(result.entrySet())) {
            var prior = entry.getValue();
            int add = Math.min(64 - prior.quantity(), remaining.getOrDefault(prior.itemKind(), 0));
            if (add == 0) continue;
            result.put(entry.getKey(), new Stack(prior.itemKind(), prior.quantity() + add));
            remaining.put(prior.itemKind(), remaining.get(prior.itemKind()) - add);
        }
        for (var entry : remaining.entrySet()) {
            int left = entry.getValue();
            for (int slot = 0; left > 0 && slot < capacity; slot++) {
                if (unavailable.contains(slot) || result.containsKey(slot)) continue;
                int count = Math.min(64, left);
                result.put(slot, new Stack(entry.getKey(), count)); left -= count;
            }
            if (left > 0) throw new CapacityUnavailable();
        }
        return Map.copyOf(result);
    }
}
