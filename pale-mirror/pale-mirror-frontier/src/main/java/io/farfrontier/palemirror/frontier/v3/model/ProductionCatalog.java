package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Ruleset-pinned recipe and fuel properties. No inventories, jobs or machine state. */
public record ProductionCatalog(List<ProductionRecipe> recipes, List<Fuel> fuels, List<ProductionEquipment> equipment) {
    public record Fuel(String itemKind, long heatUnits) {
        public Fuel {
            ProductionRecipe.requireItemKey(itemKind);
            if (heatUnits < 1 || heatUnits > 1_000_000_000L)
                throw new IllegalArgumentException("invalid fuel energy");
        }
    }
    public ProductionCatalog {
        recipes = List.copyOf(Objects.requireNonNull(recipes));
        fuels = List.copyOf(Objects.requireNonNull(fuels));
        equipment = List.copyOf(Objects.requireNonNull(equipment));
        if (recipes.isEmpty() || recipes.size() > 256 || fuels.size() > 256
                || recipes.stream().map(ProductionRecipe::id).distinct().count() != recipes.size()
                || fuels.stream().map(Fuel::itemKind).distinct().count() != fuels.size()
                || equipment.isEmpty() || equipment.size() > 256
                || equipment.stream().map(ProductionEquipment::id).distinct().count() != equipment.size())
            throw new IllegalArgumentException("invalid closed production catalog");
    }
    public ProductionRecipe require(ProductionRecipe.Id id) {
        Objects.requireNonNull(id, "declared recipe identity");
        return recipes.stream().filter(recipe -> recipe.id().equals(id)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("unregistered production recipe " + id.value()));
    }
    public Fuel requireFuel(String kind) {
        return fuels.stream().filter(fuel -> fuel.itemKind().equals(kind)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("unregistered production fuel " + kind));
    }
    public ProductionEquipment requireEquipment(ProductionEquipment.Id id) {
        return equipment.stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("unregistered production equipment " + id.value()));
    }
    public String canonicalText() {
        return recipes.stream().sorted(Comparator.comparing(recipe -> recipe.id().value()))
                .map(ProductionRecipe::canonicalText).collect(java.util.stream.Collectors.joining(";"))
                + "|" + fuels.stream().sorted(Comparator.comparing(Fuel::itemKind))
                .map(fuel -> fuel.itemKind() + ":" + fuel.heatUnits()).collect(java.util.stream.Collectors.joining(";"))
                + "|" + equipment.stream().sorted(Comparator.comparing(value -> value.id().value()))
                .map(ProductionEquipment::canonicalText).collect(java.util.stream.Collectors.joining(";"));
    }
    /** Exact old balance for installed historical selectors; new heated profiles are explicit. */
    public static ProductionCatalog initial() {
        return new ProductionCatalog(List.of(new ProductionRecipe(BakeryRecipes.BREAD,
                ProductionStationSpec.Capability.BAKING,
                new ProductionRecipe.Ingredient(Set.of("minecraft:wheat"), 1),
                new ProductionRecipe.Product("minecraft:bread", 1), 0,
                ProductionRecipe.ProcessingMode.ATTENDED)), List.of(), List.of(ProductionEquipment.bench()));
    }
    public static ProductionCatalog fuelledGraybox() {
        return new ProductionCatalog(List.of(new ProductionRecipe(BakeryRecipes.BREAD,
                ProductionStationSpec.Capability.BAKING, new ProductionRecipe.Ingredient(Set.of("minecraft:wheat"), 3),
                new ProductionRecipe.Product("minecraft:bread", 1), 80, ProductionRecipe.ProcessingMode.ATTENDED)),
                List.of(new Fuel("minecraft:coal", 1600)), List.of(ProductionEquipment.bench()));
    }
}
