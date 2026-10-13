package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ProductionCatalogTest {
    @Test void completeBatchesAndDeclaredMembershipRejectWrongOrPartialInputs() {
        var recipe = recipe(3, 1, 80);
        assertEquals(2, recipe.outputForInput(6));
        assertThrows(IllegalArgumentException.class, () -> recipe.outputForInput(7));
        assertThrows(IllegalArgumentException.class, () -> recipe.requireTransformation(recipe.capability(),
                Map.of("minecraft:wheat", 6), "minecraft:bread", 6));
        assertThrows(IllegalArgumentException.class, () -> recipe.requireTransformation(recipe.capability(),
                Map.of("minecraft:cobblestone", 6), "minecraft:bread", 2));
        recipe.requireTransformation(recipe.capability(), Map.of("minecraft:wheat", 6), "minecraft:bread", 2);
    }
    @Test void closedCatalogRejectsDuplicateOrMissingDeclarationsAndPinsFuelAndRecipeValues() {
        var recipe = recipe(1, 1, 80);
        var fuel = new ProductionCatalog.Fuel("minecraft:coal", 1_600);
        var catalog = new ProductionCatalog(List.of(recipe), List.of(fuel), List.of(ProductionEquipment.bench()));
        assertEquals(recipe, catalog.require(recipe.id()));
        assertEquals(fuel, catalog.requireFuel("minecraft:coal"));
        assertThrows(IllegalArgumentException.class, () -> new ProductionCatalog(List.of(recipe, recipe), List.of(fuel), List.of(ProductionEquipment.bench())));
        assertThrows(IllegalArgumentException.class, () -> new ProductionCatalog(List.of(recipe), List.of(fuel, fuel), List.of(ProductionEquipment.bench())));
        assertThrows(IllegalArgumentException.class, () -> catalog.require(new ProductionRecipe.Id("pale_mirror:missing")));
        assertThrows(IllegalArgumentException.class, () -> catalog.requireFuel("minecraft:stone"));
        assertThrows(NullPointerException.class, () -> catalog.require(null));
        assertNotEquals(catalog.canonicalText(), new ProductionCatalog(List.of(recipe(1, 1, 81)), List.of(fuel), List.of(ProductionEquipment.bench())).canonicalText());
        assertNotEquals(catalog.canonicalText(), new ProductionCatalog(List.of(recipe),
                List.of(new ProductionCatalog.Fuel("minecraft:coal", 1_601)), List.of(ProductionEquipment.bench())).canonicalText());
    }
    @Test void productionBalanceCannotBypassRulesetHashOrRetuneHistoricalSelectors() {
        var base = FrontierRulesets.production();
        var heated = new ProductionCatalog(List.of(recipe(1, 1, 80)), List.of(new ProductionCatalog.Fuel("minecraft:coal", 1_600)), List.of(ProductionEquipment.bench()));
        assertThrows(IllegalArgumentException.class, () -> copy(base, 24, heated));
        assertNotEquals(copy(base, 25, ProductionCatalog.initial()).contentSha256(), copy(base, 25, heated).contentSha256());
        assertEquals(ProductionCatalog.initial(), base.production());
    }
    private static ProductionRecipe recipe(int input, int output, long heat) {
        return new ProductionRecipe(BakeryRecipes.BREAD, ProductionStationSpec.Capability.BAKING,
                new ProductionRecipe.Ingredient(Set.of("minecraft:wheat"), input),
                new ProductionRecipe.Product("minecraft:bread", output), heat, ProductionRecipe.ProcessingMode.ATTENDED);
    }
    private static FrontierRuleset copy(FrontierRuleset base, int schema, ProductionCatalog catalog) {
        return new FrontierRuleset("frontier-v3-catalog-test", schema, base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(),
                base.resourceHarvestColdTravelTicksPerEdge(), base.workCatalog(), base.goodsTrade(),
                base.initialSettlementStocks(), base.labour(), base.expedition(), base.extraction(), base.execution(), catalog);
    }
}
