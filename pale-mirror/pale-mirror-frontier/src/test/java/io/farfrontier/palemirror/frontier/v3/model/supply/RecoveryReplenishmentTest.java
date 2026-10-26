package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryReplenishmentTest {
    private static final SupplyPolicy MATERIAL = new SupplyPolicy(500, 4, 8, 100, 500, false);
    private static SupplyPlanner.Stock stock(long available, long target) {
        return new SupplyPlanner.Stock(available, 0, 0, target, 0, 0, 1, MATERIAL);
    }
    private static SupplyPlanner.Process smelter() {
        return smelter("fuel");
    }
    private static SupplyPlanner.Process smelter(String fuel) {
        return new SupplyPlanner.Process("smelter", "metal", 1, Map.of("ore", 1),
                1, Map.of(fuel, 8L), 0, 10, 10, 6);
    }
    @Test void directServiceAndUpstreamRecipeCannotShareOnePurchasedFuelUnit() {
        for (var fuel : List.of("a-fuel", "z-fuel")) {
            var plan = new SupplyPlanner().assess(Map.of("metal", stock(0, 6), "ore", stock(6, 0), fuel, stock(0, 1)),
                    List.of(smelter(fuel)), List.of(), SourcingMarket.none(), Map.of("metal", 6L, fuel, 1L)).plan();
            assertEquals(2, plan.require(fuel).purchase(), "one for the direct service and one for six smelting operations");
            assertEquals(1, plan.inputRequirements().get(fuel), "upstream report must not repeat the owner's direct requirement");
            assertEquals(6, plan.require("metal").recoveryProduction());
        }
    }
    @Test void datedLocalOutputCoversRecoveryButUnsecuredProductionCannot() {
        var need = new SupplyReservePlanner.Need(SupplyReservePlanner.Rate.ZERO, 1);
        var ready = new SupplyPlanner.Demand(14, 2, 0, 2, 900,
                Set.of(SupplyPlanner.Reason.PRODUCTION_FUEL), Optional.empty(), 0);
        assertFalse(TripDispatchPolicy.uncovered(ready, need, 0, List.of(), 1000, 100));
        var unbacked = new SupplyPlanner.Demand(14, 0, 2, 2, 900,
                Set.of(SupplyPlanner.Reason.PRODUCTION_FUEL), Optional.empty(), 0);
        assertTrue(TripDispatchPolicy.uncovered(unbacked, need, 0, List.of(), 1000, 100));
    }
    @Test void fuelAlreadyAllocatedToDirectServiceCannotBackAnotherTimelyRecipe() {
        var plan = new SupplyPlanner().assess(Map.of("metal", stock(0, 6), "ore", stock(6, 0), "fuel", stock(1, 1)),
                List.of(smelter()), List.of(), SourcingMarket.none(), Map.of("metal", 6L, "fuel", 1L)).plan();
        assertEquals(1, plan.require("fuel").purchase());
        assertEquals(0, plan.require("metal").production(), "unquoted replacement fuel cannot certify existing fuel twice");
        assertEquals(6, plan.require("metal").recoveryProduction());
    }
    @Test void obsoleteDeadlineDoesNotMakeAnAlreadyCoveredRestartANewEmergency() {
        var need = new SupplyReservePlanner.Need(SupplyReservePlanner.Rate.ZERO, 1);
        var refill = new SupplyPlanner.Demand(14, 0, 0, 2, 0,
                Set.of(SupplyPlanner.Reason.PRODUCTION_FUEL), Optional.empty(), 0);
        assertFalse(TripDispatchPolicy.uncovered(refill, need, 2, List.of(), 1000, 100));
        assertFalse(TripDispatchPolicy.uncovered(refill, need, 0,
                List.of(new SupplyPlanner.Arrival(2, 900)), 1000, 100));
        assertTrue(TripDispatchPolicy.uncovered(refill, need, 0,
                List.of(new SupplyPlanner.Arrival(2, 1200)), 1000, 100));
        var fundedLot = ReplenishmentLot.choose(16, 100, 2, LotTransportCost.fixed(64, 15, 15));
        assertEquals(TripDispatchPolicy.Cause.ACCUMULATING, TripDispatchPolicy.assess(
                List.of(new TripDispatchPolicy.Goods(1, fundedLot, refill, false)), 1000, 100));
        assertEquals(TripDispatchPolicy.Cause.SUPPLY_DEADLINE, TripDispatchPolicy.assess(
                List.of(new TripDispatchPolicy.Goods(1, fundedLot, refill, true)), 1000, 100),
                "the only useful partial recovery is still permitted without a fixed minimum");
    }
}
