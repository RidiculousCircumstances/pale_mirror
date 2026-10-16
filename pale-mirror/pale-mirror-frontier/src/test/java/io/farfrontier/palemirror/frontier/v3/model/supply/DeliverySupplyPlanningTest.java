package io.farfrontier.palemirror.frontier.v3.model.supply;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeliverySupplyPlanningTest {
    private static final SupplyPolicy FOOD = new SupplyPolicy(1000, 1, 64, 100, 500, true);
    private static final SupplyPolicy MATERIAL = new SupplyPolicy(500, 1, 64, 100, 500, false);
    private static final SupplyPlanner.Process BAKERY = new SupplyPlanner.Process("bakery", "bread", 1,
            Map.of("wheat", 3), 80, Map.of("coal", 1600L), 0, 100, 10, 100);
    private static Map<String, SupplyPlanner.Stock> stocks(List<SupplyPlanner.Arrival> coal) {
        return Map.of("bread", new SupplyPlanner.Stock(20, 0, 0, 100, 0, 1, 100, FOOD),
                "wheat", new SupplyPlanner.Stock(240, 0, 0, 0, 0, 0, 1, MATERIAL),
                "coal", new SupplyPlanner.Stock(0, 4, 0, 0, 0, 0, 1, MATERIAL, coal));
    }
    @Test void logisticsEtaCountsBeforeProductionAndUnknownOrLateDeliveryRestoresFallback() {
        var planner = new SupplyPlanner();
        var onTime = planner.plan(stocks(List.of(new SupplyPlanner.Arrival(4, 1000))), List.of(BAKERY));
        assertEquals(80, onTime.require("bread").production());
        assertEquals(0, onTime.require("bread").purchase());
        var late = planner.plan(stocks(List.of(new SupplyPlanner.Arrival(4, 2000))), List.of(BAKERY));
        assertEquals(0, late.require("bread").production());
        assertEquals(80, late.require("bread").purchase());
        var stopped = planner.plan(stocks(List.of()), List.of(BAKERY));
        assertEquals(80, stopped.require("bread").purchase());
        assertEquals(0, stopped.require("coal").purchase(), "an ETA loss does not duplicate the existing raw-material order");
    }
    @Test void onlyTheTimelyPartOfIncomingCargoFundsOutput() {
        var plan = new SupplyPlanner().plan(stocks(List.of(new SupplyPlanner.Arrival(1, 100),
                new SupplyPlanner.Arrival(3, 3000))), List.of(BAKERY));
        assertEquals(20, plan.require("bread").production());
        assertEquals(60, plan.require("bread").purchase());
    }
    @Test void arrivingInputsCannotFundTwoDifferentProductsOrStealRetainedJobInputs() {
        var stocks = Map.of("a", new SupplyPlanner.Stock(10, 0, 0, 20, 0, 0, 1, FOOD),
                "b", new SupplyPlanner.Stock(10, 0, 0, 20, 0, 0, 1, FOOD),
                "grain", new SupplyPlanner.Stock(0, 10, 0, 0, 0, 0, 1, MATERIAL, List.of(new SupplyPlanner.Arrival(10, 100))));
        var a = new SupplyPlanner.Process("a", "a", 1, Map.of("grain", 1), 0, Map.of(), 0, 1, 1, 10);
        var b = new SupplyPlanner.Process("b", "b", 1, Map.of("grain", 1), 0, Map.of(), 0, 1, 1, 10);
        var planner = new SupplyPlanner(); var plan = planner.plan(stocks, List.of(a, b));
        assertEquals(10, plan.require("a").production() + plan.require("b").production());
        plan = planner.plan(stocks, List.of(a, b), List.of(new SupplyPlanner.Dependency("a", "grain", 10, SupplyPlanner.Reason.PRODUCTION_INPUT)));
        assertEquals(0, plan.require("a").production() + plan.require("b").production());
    }
    @Test void timedQuantityMustBeASubsetOfActualIncomingObligations() {
        assertThrows(IllegalArgumentException.class, () -> new SupplyPlanner.Stock(0, 3, 0, 0, 0, 0, 1,
                MATERIAL, List.of(new SupplyPlanner.Arrival(4, 100))));
    }
}
