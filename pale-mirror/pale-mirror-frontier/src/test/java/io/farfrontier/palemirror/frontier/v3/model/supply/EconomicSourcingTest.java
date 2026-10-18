package io.farfrontier.palemirror.frontier.v3.model.supply;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EconomicSourcingTest {
    private static final SupplyPolicy POLICY = new SupplyPolicy(1000, 1, 64, 100, 500, false);
    private static SupplyPlanner.Stock stock(long held, long target) {
        return new SupplyPlanner.Stock(held, 0, 0, target, 0, 0, 100, POLICY);
    }
    private static SupplyPlanner.Process process(String id, String product, Map<String, Integer> inputs) {
        return new SupplyPlanner.Process(id, product, 1, inputs, 0, Map.of(), 0, 10, 1, 100, 10);
    }
    private static SourcingMarket.Offer offer(String id, String item, long count, long price) {
        return new SourcingMarket.Offer(id, item, count, price, price, 20, 0);
    }
    @Test void cheapPartialPurchaseAndOwnProductionShareOneDemandWithoutInventingIncomingGoods() {
        var facts = Map.of("food", stock(0, 10), "input", stock(10, 0));
        var own = process("station", "food", Map.of("input", 1));
        var market = new SourcingMarket(12, 100, Map.of("station", 10L), List.of(offer("seller", "food", 10, 2)));
        var plan = new SupplyPlanner().plan(facts, List.of(own), List.of(), market);
        assertEquals(6, plan.require("food").purchase());
        assertEquals(4, plan.require("food").production());
        assertEquals(4, plan.require("input").protectedInput());
        assertEquals(Map.of("food", 12L), plan.preferredPurchaseSpending());
        assertEquals(0, facts.get("food").incoming(), "quote advice is not a delivery promise");
        var expensive = new SourcingMarket(100, 100, Map.of("station", 10L), List.of(offer("seller", "food", 10, 12)));
        assertEquals(10, new SupplyPlanner().plan(facts, List.of(own), List.of(), expensive).require("food").production());
    }
    @Test void budgetIsSharedAcrossCommoditiesAndTripOverheadCountsForTheActuallyAffordableQuantity() {
        var processes = List.of(process("a", "a", Map.of()), process("b", "b", Map.of()));
        var market = new SourcingMarket(18, 100, Map.of("a", 10L, "b", 10L),
                List.of(offer("a-seller", "a", 5, 3), offer("b-seller", "b", 5, 3)));
        var plan = new SupplyPlanner().plan(Map.of("a", stock(0, 5), "b", stock(0, 5)), processes, List.of(), market);
        assertEquals(18, plan.preferredPurchaseSpending().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(5, plan.require("a").purchase()); assertEquals(1, plan.require("b").purchase());
        assertEquals(4, plan.require("b").production());
        var small = new SourcingMarket(3, 0, Map.of("a", 10L),
                List.of(new SourcingMarket.Offer("trip", "a", 64, 3, 3, 20, 20)));
        assertEquals(0, new SupplyPlanner().plan(Map.of("a", stock(0, 5)), List.of(processes.getFirst()), List.of(), small)
                .require("a").purchase(), "one affordable unit cannot amortize a whole caravan over 64 unbought units");
    }
    @Test void unavailableCashLateOffersAndSmallPriceOscillationsDoNotDisplaceOwnWork() {
        var own = process("own", "food", Map.of());
        var food = new SupplyPolicy(1000, 1, 64, 100, 500, true);
        var facts = Map.of("food", new SupplyPlanner.Stock(1, 0, 0, 3, 0, 1, 100, food));
        for (var market : List.of(new SourcingMarket(0, 100, Map.of("own", 10L), List.of(offer("s", "food", 3, 1))),
                new SourcingMarket(100, 100, Map.of("own", 10L), List.of(offer("s", "food", 3, 9))),
                new SourcingMarket(100, 100, Map.of("own", 10L), List.of(new SourcingMarket.Offer("s", "food", 3, 1, 1, Long.MAX_VALUE, 0))))) {
            assertEquals(2, new SupplyPlanner().plan(facts, List.of(own), List.of(), market).require("food").production());
        }
    }
    @Test void biologicalWaitingIsNotLabourAndYieldChangesUnitCost() {
        var fertile = new SupplyPlanner.Process("fertile", "grain", 4, Map.of(), 0, Map.of(), 0, 10000, 100, 64, 40);
        var rocky = new SupplyPlanner.Process("rocky", "grain", 1, Map.of(), 0, Map.of(), 0, 20000, 100, 64, 40);
        var costs = SourcingValuation.processes(List.of(fertile, rocky), Map.of("grain", 100L), List.of(), 2);
        assertEquals(20, costs.get("fertile")); assertEquals(80, costs.get("rocky"));
        var bakery = process("bakery", "food", Map.of("grain", 3));
        assertEquals(80, SourcingValuation.processes(List.of(fertile, bakery), Map.of("grain", 100L, "food", 500L), List.of(), 2).get("bakery"));
    }
    @Test void historicalCheapInputIsNotAnAvailableSupplierButRealLocalProductionRemainsAnAlternative() {
        var bakery = process("bakery", "food", Map.of("grain", 3));
        var prices = Map.of("grain", 1L, "food", 100L);
        var offers = List.of(offer("dear", "grain", 64, 8), offer("actual", "grain", 64, 5));
        assertEquals(25, SourcingValuation.processes(List.of(bakery), prices, offers, 1).get("bakery"));
        var field = new SupplyPlanner.Process("field", "grain", 1, Map.of(), 0, Map.of(), 0, 1, 1, 64, 2);
        assertEquals(16, SourcingValuation.processes(List.of(bakery, field), prices, offers, 1).get("bakery"));
        assertEquals(13, SourcingValuation.processes(List.of(bakery), prices, List.of(), 1).get("bakery"),
                "missing market evidence retains an explicitly estimated reference, not a supply promise");
    }
    @Test void sharedWorkerCannotSupplyTwoWholeProductionCapacitiesAtOnce() {
        var first = new SupplyPlanner.Process("a", "a", 1, Map.of(), 0, Map.of(), 0, 1, 1, 100, 10, Map.of("worker", 50L));
        var second = new SupplyPlanner.Process("b", "b", 1, Map.of(), 0, Map.of(), 0, 1, 1, 100, 10, Map.of("worker", 50L));
        var plan = new SupplyPlanner().plan(Map.of("a", stock(0, 4), "b", stock(0, 4)), List.of(first, second));
        assertEquals(4, plan.require("a").production());
        assertEquals(1, plan.require("b").production());
        assertEquals(3, plan.require("b").purchase());
        var forged = new SupplyPlanner.Process("b", "b", 1, Map.of(), 0, Map.of(), 0, 1, 1, 100, 10, Map.of("worker", 100L));
        assertThrows(IllegalArgumentException.class, () -> new SupplyPlanner().plan(
                Map.of("a", stock(0, 4), "b", stock(0, 4)), List.of(first, forged)));
    }
}
