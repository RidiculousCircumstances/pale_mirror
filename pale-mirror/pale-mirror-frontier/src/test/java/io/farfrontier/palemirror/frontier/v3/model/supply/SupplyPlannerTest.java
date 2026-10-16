package io.farfrontier.palemirror.frontier.v3.model.supply;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SupplyPlannerTest {
    @Test void productionPriorityUsesDeadlineAndRelativeShortageNotACommodityNameOrRawStackSize() {
        var urgent = new SupplyPlanner.Demand(1, 0, 1, 100,
                Set.of(SupplyPlanner.Reason.CRITICAL_PRODUCTION_DEADLINE, SupplyPlanner.Reason.PRODUCTION_FUEL), Optional.empty());
        var ordinary = SupplyPriority.from(192, 192, Optional.empty(), 100);
        var critical = SupplyPriority.from(1, 8, Optional.of(urgent), 100);
        assertTrue(critical.compareTo(ordinary) < 0);
        assertTrue(critical.withAssigned(1).compareTo(ordinary) > 0,
                "one already assigned unit covers this deficit without inventing a whole future batch");
        assertTrue(SupplyPriority.from(4, 8, Optional.empty(), 0)
                .compareTo(SupplyPriority.from(64, 192, Optional.empty(), 0)) < 0);
        assertEquals(0, SupplyPriority.from(4, 8, Optional.empty(), 0)
                .compareTo(SupplyPriority.from(96, 192, Optional.empty(), 0)));
        assertDoesNotThrow(() -> SupplyPriority.from(Long.MAX_VALUE, Long.MAX_VALUE, Optional.empty(), 0)
                .compareTo(SupplyPriority.from(Long.MAX_VALUE - 1, Long.MAX_VALUE, Optional.empty(), 0)));
    }
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
        assertEquals(0, plan.require("bread").production()); assertEquals(80, plan.require("bread").purchase());
        assertEquals(80, plan.require("bread").recoveryProduction(), "unsecured inputs retain intent, not supply coverage");
        assertEquals(240, plan.require("wheat").purchase()); assertEquals(4, plan.require("coal").purchase());
        assertTrue(plan.require("coal").reasons().contains(SupplyPlanner.Reason.PRODUCTION_FUEL));
        assertFalse(plan.require("coal").urgent(100));
    }
    @Test void overdueFoodKeepsEmergencyPurchaseAndRestoresItsProductionChain() {
        var stocks = Map.of("bread", stock(0, 0, 100, 1, FOOD), "wheat", stock(0, 0, 0, 0, MATERIAL), "coal", stock(0, 0, 0, 0, MATERIAL));
        var process = new SupplyPlanner.Process("bakery", "bread", 1, Map.of("wheat", 3), 80, Map.of("coal", 1600L), 0, 100, 10, 100);
        var plan = new SupplyPlanner().plan(stocks, List.of(process));
        assertEquals(100, plan.require("bread").purchase()); assertEquals(300, plan.require("wheat").purchase());
        assertEquals(0, plan.require("bread").production(), "late recovery is not timely coverage");
        assertEquals(100, plan.require("bread").recoveryProduction());
        assertEquals(5, plan.require("coal").purchase());
        assertTrue(plan.require("coal").urgent(100));
        assertTrue(plan.require("wheat").urgent(100));
        assertTrue(plan.require("bread").urgent(100));
    }
    @Test void promisedOutputStillPublishesUnfundedDependenciesWithoutNewCapacity() {
        var stocks = Map.of("bread", new SupplyPlanner.Stock(0, 0, 21, 21, 0, 1, 100, FOOD),
                "coal", stock(0, 0, 8, 0, MATERIAL));
        var commitment = new SupplyPlanner.Dependency("bread", "coal", 2, SupplyPlanner.Reason.PRODUCTION_FUEL);
        var plan = new SupplyPlanner().plan(stocks, List.of(), List.of(commitment));
        assertEquals(21, plan.require("bread").purchase(), "a retained batch without fuel cannot cover the food shortage");
        assertEquals(0, plan.require("bread").recoveryProduction(), "do not recreate the already retained job");
        assertEquals(8, plan.require("coal").purchase(), "dependency and stock replenishment overlap rather than sum");
        assertEquals(2, plan.require("coal").protectedInput());
        assertTrue(plan.require("coal").urgent(100));
        var funded = new HashMap<>(stocks); funded.put("coal", stock(0, 8, 8, 0, MATERIAL));
        assertEquals(0, new SupplyPlanner().plan(funded, List.of(), List.of(commitment)).require("coal").purchase());
        assertEquals(21, new SupplyPlanner().plan(funded, List.of(), List.of(commitment)).require("bread").purchase(),
                "an order without an arrival bound does not prove timely production");
        funded.put("coal", stock(8, 0, 8, 0, MATERIAL));
        assertEquals(0, new SupplyPlanner().plan(funded, List.of(), List.of(commitment)).require("bread").purchase(),
                "actual delivery backs the retained job and withdraws the fallback need");
        assertThrows(IllegalArgumentException.class, () -> new SupplyPlanner().plan(stocks, List.of(), List.of(
                commitment, new SupplyPlanner.Dependency("coal", "bread", 1, SupplyPlanner.Reason.PRODUCTION_INPUT))));
    }
    @Test void urgentFuelRequiresAnActualDownstreamDeadlineAndSupplyTimeCountsBeforeProduction() {
        var process = new SupplyPlanner.Process("bakery", "bread", 1, Map.of("wheat", 3), 80, Map.of("coal", 1600L), 0, 200, 10, 100);
        var immediateRestock = new SupplyPolicy(1000, 32, 64, 100, 500, true);
        var stocks = Map.of("bread", stock(3, 0, 4, 1, immediateRestock), "wheat", stock(0, 0, 0, 0, MATERIAL), "coal", stock(0, 0, 0, 0, MATERIAL));
        var plan = new SupplyPlanner().plan(stocks, List.of(process));
        assertEquals(0, plan.require("bread").production());
        assertEquals(1, plan.require("bread").purchase());
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
        assertEquals(24, grainPlan.require("wheat").purchase(), "keep restoring the unfunded remainder");
        assertEquals(8, grainPlan.require("bread").recoveryProduction());
        var fuelStocks = new HashMap<>(stocks); fuelStocks.put("wheat", stock(30, 0, 0, 0, MATERIAL));
        var fuelLimited = new SupplyPlanner.Process("bakery", "bread", 1, Map.of("wheat", 3),
                80, Map.of("coal", 1600L), 80, 10, 5, 10);
        var fuelPlan = new SupplyPlanner().plan(fuelStocks, List.of(fuelLimited));
        assertEquals(1, fuelPlan.require("bread").production());
        assertEquals(8, fuelPlan.require("bread").purchase());
        assertEquals(1, fuelPlan.require("coal").purchase());
        assertEquals(8, fuelPlan.require("bread").recoveryProduction());
    }
    @Test void deliveryAndLossReassessCoverageWithoutAClockBasedImaginaryPurchase() {
        var stocks = new HashMap<>(Map.of("bread", stock(20, 0, 100, 1, FOOD),
                "wheat", stock(240, 0, 0, 0, MATERIAL), "coal", stock(0, 4, 0, 0, MATERIAL)));
        var process = new SupplyPlanner.Process("bakery", "bread", 1, Map.of("wheat", 3),
                80, Map.of("coal", 1600L), 0, 100, 10, 100);
        var planner = new SupplyPlanner();
        assertEquals(80, planner.plan(stocks, List.of(process)).require("bread").purchase());
        assertEquals(0, planner.plan(stocks, List.of(process)).require("coal").purchase(), "do not duplicate the admitted order");
        stocks.put("coal", stock(4, 0, 0, 0, MATERIAL));
        assertEquals(80, planner.plan(stocks, List.of(process)).require("bread").production());
        assertEquals(0, planner.plan(stocks, List.of(process)).require("bread").purchase());
        stocks.put("coal", stock(0, 0, 0, 0, MATERIAL));
        assertEquals(80, planner.plan(stocks, List.of(process)).require("bread").purchase());
        assertEquals(4, planner.plan(stocks, List.of(process)).require("coal").purchase());
    }
    @Test void twoProcessesCannotCountTheSamePhysicalInputsTwice() {
        var stocks = Map.of("a", stock(10, 0, 20, 0, FOOD), "b", stock(10, 0, 20, 0, FOOD),
                "grain", stock(10, 0, 0, 0, MATERIAL));
        var a = new SupplyPlanner.Process("a", "a", 1, Map.of("grain", 1), 0, Map.of(), 0, 1, 1, 10);
        var b = new SupplyPlanner.Process("b", "b", 1, Map.of("grain", 1), 0, Map.of(), 0, 1, 1, 10);
        var plan = new SupplyPlanner().plan(stocks, List.of(a, b));
        assertEquals(10, plan.require("a").production() + plan.require("b").production());
        assertEquals(10, plan.require("a").purchase() + plan.require("b").purchase());
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
    @Test void anAgedLargeOrderDoesNotDispatchEachNewlyAvailableItem() {
        var ordinary = new SupplyPlanner.Demand(192, 0, 0, Long.MAX_VALUE,
                Set.of(SupplyPlanner.Reason.STOCK_REPLENISHMENT), Optional.empty());
        assertEquals(TripDispatchPolicy.Cause.ACCUMULATING, TripDispatchPolicy.assess(List.of(
                new TripDispatchPolicy.Goods(1, 10000, MATERIAL, ordinary))));
        assertEquals(TripDispatchPolicy.Cause.MAXIMUM_WAIT, TripDispatchPolicy.assess(List.of(
                new TripDispatchPolicy.Goods(MATERIAL.preferredLot(), 10000, MATERIAL, ordinary))));
    }
}
