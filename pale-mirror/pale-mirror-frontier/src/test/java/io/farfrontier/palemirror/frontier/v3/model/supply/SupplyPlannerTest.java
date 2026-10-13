package io.farfrontier.palemirror.frontier.v3.model.supply;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SupplyPlannerTest {
    private static final SupplyPolicy FOOD = new SupplyPolicy(500, 32, 64, 100, 500, true);
    private static final SupplyPolicy MATERIAL = new SupplyPolicy(500, 4, 8, 100, 500, false);
    private static SupplyPlanner.Stock stock(long held, long incoming, long target, long use, SupplyPolicy policy) {
        return new SupplyPlanner.Stock(held, incoming, 0, target, 0, use, 100, policy);
    }
    @Test void replenishesToUpperTargetWithoutRoundingAndDoesNotDoubleCountIncoming() {
        var planner = new SupplyPlanner();
        assertEquals(100, planner.plan(Map.of("bread", stock(0, 0, 100, 1, FOOD)), List.of()).require("bread").purchase());
        assertEquals(36, planner.plan(Map.of("bread", stock(0, 64, 100, 1, FOOD)), List.of()).require("bread").purchase());
        assertEquals(0, planner.plan(Map.of("bread", stock(0, 100, 100, 1, FOOD)), List.of()).require("bread").purchase());
        assertEquals(0, planner.plan(Map.of("bread", stock(75, 0, 100, 1, FOOD)), List.of()).require("bread").purchase());
    }
    @Test void expandsProductionInputsAndFuelButAnEmptyFuelStoreAloneIsNotAnEmergency() {
        var stocks = Map.of("bread", stock(20, 0, 100, 1, FOOD), "wheat", stock(0, 0, 0, 0, MATERIAL), "coal", stock(0, 0, 0, 0, MATERIAL));
        var process = new SupplyPlanner.Process("bakery", "bread", 1, Map.of("wheat", 3), 80, Map.of("coal", 1600L), 0, 100, 10, 100);
        var plan = new SupplyPlanner().plan(stocks, List.of(process));
        assertEquals(80, plan.require("bread").production()); assertEquals(0, plan.require("bread").purchase());
        assertEquals(240, plan.require("wheat").purchase()); assertEquals(4, plan.require("coal").purchase());
        assertTrue(plan.require("coal").reasons().contains(SupplyPlanner.Reason.PRODUCTION_FUEL));
        assertFalse(plan.require("coal").urgent(100));
    }
    @Test void buysReadyFoodWhenProductionCannotMeetTheActualFoodDeadline() {
        var stocks = Map.of("bread", stock(0, 0, 100, 1, FOOD), "wheat", stock(0, 0, 0, 0, MATERIAL), "coal", stock(0, 0, 0, 0, MATERIAL));
        var process = new SupplyPlanner.Process("bakery", "bread", 1, Map.of("wheat", 3), 80, Map.of("coal", 1600L), 0, 100, 10, 100);
        var plan = new SupplyPlanner().plan(stocks, List.of(process));
        assertEquals(100, plan.require("bread").purchase()); assertEquals(0, plan.require("wheat").purchase());
        assertTrue(plan.require("bread").urgent(100));
    }
    @Test void urgentFuelRequiresAnActualDownstreamDeadlineAndSupplyTimeCountsBeforeProduction() {
        var process = new SupplyPlanner.Process("bakery", "bread", 1, Map.of("wheat", 3), 80, Map.of("coal", 1600L), 0, 200, 10, 100);
        var immediateRestock = new SupplyPolicy(1000, 32, 64, 100, 500, true);
        var stocks = Map.of("bread", stock(3, 0, 4, 1, immediateRestock), "wheat", stock(0, 0, 0, 0, MATERIAL), "coal", stock(0, 0, 0, 0, MATERIAL));
        var plan = new SupplyPlanner().plan(stocks, List.of(process));
        assertEquals(1, plan.require("bread").production());
        assertEquals(100, plan.require("coal").deadlineTicks());
        assertTrue(plan.require("coal").urgent(100));
        var late = new HashMap<>(stocks); late.put("bread", stock(2, 0, 4, 1, FOOD));
        assertEquals(2, new SupplyPlanner().plan(late, List.of(process)).require("bread").purchase());
    }
    @Test void oneAvailableBatchCannotPromiseTheWholeVolumeBeforeMissingGrainOrFuelArrives() {
        var food = new SupplyPolicy(1000, 1, 64, 100, 500, true);
        var stocks = Map.of("bread", stock(1, 0, 10, 1, food),
                "wheat", stock(3, 0, 0, 0, MATERIAL), "coal", stock(0, 0, 0, 0, MATERIAL));
        var grainLimited = new SupplyPlanner.Process("bakery", "bread", 1, Map.of("wheat", 3),
                0, Map.of(), 0, 10, 5, 10);
        var grainPlan = new SupplyPlanner().plan(stocks, List.of(grainLimited));
        assertEquals(1, grainPlan.require("bread").production());
        assertEquals(8, grainPlan.require("bread").purchase());
        assertEquals(0, grainPlan.require("wheat").purchase());
        var fuelStocks = new HashMap<>(stocks); fuelStocks.put("wheat", stock(30, 0, 0, 0, MATERIAL));
        var fuelLimited = new SupplyPlanner.Process("bakery", "bread", 1, Map.of("wheat", 3),
                80, Map.of("coal", 1600L), 80, 10, 5, 10);
        var fuelPlan = new SupplyPlanner().plan(fuelStocks, List.of(fuelLimited));
        assertEquals(1, fuelPlan.require("bread").production());
        assertEquals(8, fuelPlan.require("bread").purchase());
        assertEquals(0, fuelPlan.require("coal").purchase());
    }
    @Test void anUnusedCyclicCatalogCannotHideBehindFullStocks() {
        var stocks = Map.of("bread", stock(100, 0, 100, 0, FOOD), "wheat", stock(100, 0, 100, 0, MATERIAL));
        var a = new SupplyPlanner.Process("a", "bread", 1, Map.of("wheat", 1), 0, Map.of(), 0, 1, 1, 1);
        var b = new SupplyPlanner.Process("b", "wheat", 1, Map.of("bread", 1), 0, Map.of(), 0, 1, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> new SupplyPlanner().plan(stocks, List.of(a, b)));
    }
    @Test void aSmallOrdinaryOrderWaitsButEventuallyDispatchesAndCriticalFoodDoesNotWait() {
        var ordinary = new SupplyPlanner.Demand(2, 0, 0, Long.MAX_VALUE, Set.of(SupplyPlanner.Reason.STOCK_REPLENISHMENT), Optional.empty());
        assertEquals(TripDispatchPolicy.Cause.ACCUMULATING, TripDispatchPolicy.assess(List.of(new TripDispatchPolicy.Goods(2, 0, FOOD, ordinary))));
        assertEquals(TripDispatchPolicy.Cause.MAXIMUM_WAIT, TripDispatchPolicy.assess(List.of(new TripDispatchPolicy.Goods(2, 500, FOOD, ordinary))));
        var urgent = new SupplyPlanner.Demand(2, 0, 0, 10, Set.of(SupplyPlanner.Reason.CONSUMPTION_DEADLINE), Optional.empty());
        assertEquals(TripDispatchPolicy.Cause.SUPPLY_DEADLINE, TripDispatchPolicy.assess(List.of(new TripDispatchPolicy.Goods(2, 0, FOOD, urgent))));
        assertEquals(TripDispatchPolicy.Cause.PREFERRED_LOAD, TripDispatchPolicy.assess(List.of(new TripDispatchPolicy.Goods(100, 0, FOOD, ordinary))));
    }
}
