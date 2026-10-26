package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionMaterialAllocationTest {
    private static ProductionRecipe recipe(List<ProductionRecipe.Ingredient> inputs) {
        return new ProductionRecipe(new ProductionRecipe.Id("pale_mirror:allocation-test"), ProductionStationSpec.Capability.FABRICATION,
                inputs, new ProductionRecipe.Product("minecraft:iron_pickaxe", 1), false,
                ProductionRecipe.ProcessingMode.ATTENDED, new ProductionRecipeOutcome.Individual());
    }
    private static ProductionRecipe.Ingredient input(String role, int count, String... items) {
        return new ProductionRecipe.Ingredient(new ProductionRecipe.InputId(role), Set.of(items), count);
    }
    @Test void overlappingAlternativesDoNotConsumeTheOnlyMaterialOfAnotherRequirement() {
        var recipe = recipe(List.of(input("a_flexible", 1, "minecraft:oak_planks", "minecraft:stick"),
                input("b_planks", 1, "minecraft:oak_planks")));
        var supply = Map.of("minecraft:oak_planks", 1, "minecraft:stick", 1);
        var allocation = recipe.allocate(supply, 1).orElseThrow();
        assertEquals(Map.of("minecraft:stick", 1), allocation.inputs().get(new ProductionRecipe.InputId("a_flexible")));
        assertEquals(Map.of("minecraft:oak_planks", 1), allocation.inputs().get(new ProductionRecipe.InputId("b_planks")));
        assertEquals(allocation, recipe.allocate(new TreeMap<>(supply), 1).orElseThrow());
    }
    @Test void extraHeadMaterialCannotReplaceMissingHandlesOrSatisfyTwoRequirementsAtOnce() {
        var recipe = recipe(List.of(input("head", 3, "minecraft:iron_ingot"), input("handle", 2, "minecraft:stick")));
        assertTrue(recipe.allocate(Map.of("minecraft:iron_ingot", 5), 1).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> recipe.requireTransformation(recipe.capability(),
                Map.of("minecraft:iron_ingot", 5), "minecraft:iron_pickaxe", 1));
        assertEquals(Map.of("minecraft:iron_ingot", 3, "minecraft:stick", 2),
                recipe.allocate(Map.of("minecraft:iron_ingot", 64, "minecraft:stick", 2), 1).orElseThrow().quantities());
        assertThrows(IllegalArgumentException.class, () -> recipe.requireTransformation(recipe.capability(),
                Map.of("minecraft:iron_ingot", 4, "minecraft:stick", 1), "minecraft:iron_pickaxe", 1));
    }
    @Test void duplicateRequirementsAndOversizedOperationsFailBeforeAllocation() {
        assertThrows(IllegalArgumentException.class, () -> recipe(List.of(input("head", 1, "minecraft:iron_ingot"),
                input("head", 1, "minecraft:stick"))));
        var recipe = recipe(List.of(input("head", 3, "minecraft:iron_ingot"), input("handle", 2, "minecraft:stick")));
        assertThrows(IllegalArgumentException.class, () -> recipe.allocate(Map.of("minecraft:iron_ingot", 64, "minecraft:stick", 64), 14));
    }
}
