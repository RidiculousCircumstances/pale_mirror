package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable, ruleset-hashed edible definitions. No inventory, actor or activity ownership. */
public record FoodCatalog(Map<String, Food> foods) {
    public static final String BREAD = "minecraft:bread";
    public FoodCatalog {
        foods = Map.copyOf(Objects.requireNonNull(foods, "food definitions"));
        if (foods.isEmpty() || foods.size() > 256 || foods.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(entry.getValue().itemKind())))
            throw new IllegalArgumentException("invalid closed food catalog");
    }
    public record Food(String itemKind, int nutritionPerItem, int maxPortionItems) {
        public Food {
            if (itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")
                    || nutritionPerItem < 1 || nutritionPerItem > ResidentNutrition.MAX_SATIETY_UNITS
                    || maxPortionItems < 1 || maxPortionItems > 64)
                throw new IllegalArgumentException("invalid food definition");
        }
        public int portionFor(int nutritionWanted, int available) {
            if (nutritionWanted < 0 || available < 0) throw new IllegalArgumentException("negative food demand or stock");
            return Math.min(Math.min(Math.ceilDiv(nutritionWanted, nutritionPerItem), available), maxPortionItems);
        }
    }
    public static FoodCatalog bread(int nutritionPerItem) {
        return new FoodCatalog(Map.of(BREAD, new Food(BREAD, nutritionPerItem, 64)));
    }
    public Optional<Food> find(String itemKind) { return Optional.ofNullable(foods.get(itemKind)); }
    public Food require(String itemKind) {
        return find(itemKind).orElseThrow(() -> new IllegalArgumentException("unregistered edible: " + itemKind));
    }
    public String canonicalText() {
        return new TreeMap<>(foods).values().stream()
                .map(food -> food.itemKind() + "=" + food.nutritionPerItem() + ":" + food.maxPortionItems())
                .collect(java.util.stream.Collectors.joining(";"));
    }
}
