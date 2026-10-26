package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.math.BigInteger;
import java.util.*;

/** Economic ranking of declared combustible items. Exact claims and port custody remain with their existing owners. */
public final class ProductionFuelSelection {
    public static List<ProductionCatalog.Fuel> ranked(FrontierWorldState state, SubjectId owner, long usefulTicks, long tick) {
        var participant = state.companies().goodsTrade().participants().participants().get(owner);
        var prices = new HashMap<String, Long>();
        var fuels = state.bootstrap().ruleset().production().fuels();
        for (var fuel : fuels) prices.put(fuel.itemKind(), GoodsResourceValuation.replacement(state, participant, fuel.itemKind(), tick).raw());
        return fuels.stream().sorted((a, b) -> {
            long aUseful = Math.min(Math.max(1, usefulTicks), a.burnTicks()), bUseful = Math.min(Math.max(1, usefulTicks), b.burnTicks());
            int cost = BigInteger.valueOf(prices.get(a.itemKind())).multiply(BigInteger.valueOf(bUseful))
                    .compareTo(BigInteger.valueOf(prices.get(b.itemKind())).multiply(BigInteger.valueOf(aUseful)));
            return cost != 0 ? cost : a.itemKind().compareTo(b.itemKind());
        }).toList();
    }
    /** A stock policy's declared recipe material is not free fuel just because it is combustible.
     * Existing exact job claims independently protect admitted work. */
    public static long materialBuffer(FrontierWorldState state, String kind) {
        long quantity = 0;
        var catalog = state.bootstrap().ruleset().production();
        for (var policy : catalog.stockPolicies()) {
            var recipe = catalog.require(policy.recipeId());
            if (recipe.product().itemKind().equals(kind)) quantity = Math.addExact(quantity, policy.outputReserve());
            for (var input : recipe.ingredients()) if (input.accepts(kind))
                quantity = Math.addExact(quantity, Math.multiplyExact(Math.ceilDiv((long) policy.outputReserve(), recipe.product().quantity()), input.quantity()));
        }
        return quantity;
    }
    private ProductionFuelSelection() { }
}
