package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.ToLongFunction;

/** Complete one-batch material forecast. The supplied valuation ranks declared alternatives;
 * this does not reserve stock or replace the exact allocator used by admission. */
public final class ProductionRecipeInputs {
    public static Map<String, Integer> select(ProductionRecipe recipe, ToLongFunction<String> cost) {
        var inputs = new TreeMap<String, Integer>();
        for (var requirement : recipe.ingredients()) {
            String kind = requirement.acceptedItems().stream().min(Comparator
                    .comparingLong(cost).thenComparing(String::compareTo)).orElseThrow();
            inputs.merge(kind, requirement.quantity(), Math::addExact);
        }
        return Map.copyOf(inputs);
    }
    private ProductionRecipeInputs() { }
}
