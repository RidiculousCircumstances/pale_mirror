package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionFuelReserveTest {
    @Test void fuelTrialChangesOnlyBakingHeatAndFiniteGeologicalReserves() {
        var prior = FrontierRulesets.installed("frontier-v3-quarry-graybox-r21");
        var rules = FrontierRulesets.dynamicFarmingGraybox();
        assertEquals("frontier-v3-quarry-graybox-r22", rules.id());
        assertEquals(80, prior.production().require(BakeryRecipes.BREAD).heatPerBatch());
        var recipe = rules.production().require(BakeryRecipes.BREAD);
        assertEquals(640, recipe.heatPerBatch());
        assertEquals(3, recipe.ingredient().quantity());
        assertEquals(1, recipe.product().quantity());
        assertEquals(1600, rules.production().requireFuel("minecraft:coal").heatUnits());
        assertEquals(prior.goodsTrade(), rules.goodsTrade(), "no price or market subsidy changes");
        assertEquals(prior.initialSettlementStocks(), rules.initialSettlementStocks());
        assertTrue(rules.initialSettlementStocks().stream().noneMatch(stock -> stock.itemKind().equals("minecraft:coal")));
        assertEquals(prior.fields(), rules.fields());
        assertEquals(prior.labour(), rules.labour());
        var codec = new FrontierWorldStateCodec();
        for (long seed : new long[]{125L, 20260918065L}) {
            var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:fuel-trial"), seed, rules));
            var sites = state.extractionSites();
            assertEquals(12, sites.deposits().size());
            assertEquals(12, sites.geology().deposits().size());
            long ore = sites.geology().deposits().values().stream().flatMap(deposit -> deposit.sections().stream())
                    .filter(section -> section.material().kind().equals("minecraft:coal_ore"))
                    .mapToLong(section -> section.volume().blocks()).sum();
            assertEquals(41_472, ore, "four times the previous 10,368 geological coal blocks, not warehouse grants");
            assertTrue(sites.deposits().values().stream().allMatch(deposit -> deposit.site().layout().cells().size() == 64),
                    "larger reserves must not pre-admit a larger active mining front");
            assertEquals(state, codec.decode(codec.encode(state)));
        }
        var pocket = rules.extraction().pocket().orElseThrow();
        assertThrows(IllegalArgumentException.class, () ->
                new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPocketProfile(
                        1, Integer.MAX_VALUE, 64, 0, 1, pocket.material()));
    }

    @Test void reserveCoversMaximumRateFullBatchesAndBoundaryOperation() {
        var catalog = ProductionCatalog.fuelledGraybox();
        var work = WorkCatalog.initial();
        assertEquals(264, ProductionFuelReserve.requiredItems(catalog, work, BakeryRecipes.BREAD, "minecraft:coal", 64, 1, 100_000));
        assertEquals(528, ProductionFuelReserve.requiredItems(catalog, work, BakeryRecipes.BREAD, "minecraft:coal", 64, 2, 100_000));
        assertThrows(IllegalArgumentException.class, () -> ProductionFuelReserve.requiredItems(catalog, work, BakeryRecipes.BREAD, "minecraft:coal", 2, 1, 100_000));
        assertThrows(IllegalArgumentException.class, () -> ProductionFuelReserve.requiredItems(catalog, work, BakeryRecipes.BREAD, "minecraft:stone", 64, 1, 100_000));
        assertThrows(IllegalArgumentException.class, () -> ProductionFuelReserve.requiredItems(catalog, work, BakeryRecipes.BREAD, "minecraft:coal", 64, 1, 0));
    }

    @Test void newProfilePinsFiniteCoalStocksAndTradeTargetWithoutRetuningHistoricalWorlds() {
        var rules = FrontierRulesets.installed("frontier-v3-quarry-graybox-r12");
        assertEquals("frontier-v3-quarry-graybox-r12", rules.id());
        var coal = rules.initialSettlementStocks().stream().filter(stock -> stock.itemKind().equals("minecraft:coal")).toList();
        assertEquals(12, coal.size());
        assertTrue(coal.stream().allMatch(stock -> stock.quantity() == 320));
        var commodity = rules.goodsTrade().policies().get(GoodsPolicyKind.PUBLIC_SETTLEMENT).stream()
                .filter(value -> value.itemKind().equals("minecraft:coal")).findFirst().orElseThrow();
        assertEquals(320, commodity.target(40));
        assertEquals(64, rules.goodsTrade().supplyPlanning().require("minecraft:coal").preferredTripLoad());
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:long-fuel"), 20260918065L, rules));
        assertEquals(initial, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(initial)));
        assertTrue(FrontierRulesets.installed("frontier-v3-quarry-graybox-r11").initialSettlementStocks().stream()
                .filter(stock -> stock.itemKind().equals("minecraft:coal")).allMatch(stock -> stock.quantity() == 4));
    }
}
