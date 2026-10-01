package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Objects;

/** Exact retained edible portion; lot identity is never inferred from physical slot ordering. */
public record FoodPortion(String itemKind, int nutritionPerItem, Map<SubjectId, Integer> lotQuantities) {
    public FoodPortion {
        Objects.requireNonNull(itemKind, "portion food");
        lotQuantities = Map.copyOf(Objects.requireNonNull(lotQuantities, "portion lots"));
        if (itemKind.isBlank() || nutritionPerItem < 1 || nutritionPerItem > ResidentNutrition.MAX_SATIETY_UNITS
                || lotQuantities.isEmpty() || lotQuantities.size() > 64
                || lotQuantities.values().stream().anyMatch(value -> value < 1 || value > 64)
                || lotQuantities.values().stream().mapToLong(Integer::longValue).sum() > 64)
            throw new IllegalArgumentException("invalid retained edible portion");
    }
    public int quantity() { return lotQuantities.values().stream().mapToInt(Integer::intValue).sum(); }
    public int nutritionUnits() { return Math.multiplyExact(quantity(), nutritionPerItem); }
    public void validate(FoodCatalog catalog) {
        var food = catalog.require(itemKind);
        if (nutritionPerItem != food.nutritionPerItem() || quantity() > food.maxPortionItems())
            throw new IllegalArgumentException("portion differs from its retained food policy");
    }
}
