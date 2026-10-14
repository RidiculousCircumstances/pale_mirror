package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionFuelReserveTest {
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
