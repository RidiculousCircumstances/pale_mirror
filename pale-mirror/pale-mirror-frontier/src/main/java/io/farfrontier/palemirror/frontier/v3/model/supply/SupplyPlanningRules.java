package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Ruleset-owned policy, not inferred from recipe names or resource identifier prefixes. */
public record SupplyPlanningRules(Map<String, SupplyPolicy> commodities, int maximumOrderItems,
                                  int workCargoSlots, int personalFoodItems, int maximumTripCargoStacks) {
    public SupplyPlanningRules {
        commodities = Map.copyOf(commodities);
        if (commodities.size() > 256 || maximumOrderItems < 1 || maximumOrderItems > 4096
                || workCargoSlots != 1 || personalFoodItems < 1 || personalFoodItems > 64
                || maximumTripCargoStacks < 1 || maximumTripCargoStacks > 32)
            throw new IllegalArgumentException("invalid bounded supply rules");
    }
    public static SupplyPlanningRules disabled() { return new SupplyPlanningRules(Map.of(), 64, 1, 8, 1); }
    public boolean enabled() { return !commodities.isEmpty(); }
    public SupplyPolicy require(String item) { return Objects.requireNonNull(commodities.get(item), "undeclared supply commodity " + item); }
    public String canonicalText() {
        return maximumOrderItems + ":" + workCargoSlots + ":" + personalFoodItems + ":" + maximumTripCargoStacks + "|"
                + commodities.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue().canonicalText()).collect(java.util.stream.Collectors.joining("|"));
    }
}
