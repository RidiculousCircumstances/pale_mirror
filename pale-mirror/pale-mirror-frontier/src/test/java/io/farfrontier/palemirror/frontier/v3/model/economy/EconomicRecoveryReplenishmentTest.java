package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EconomicRecoveryReplenishmentTest {
    @Test void actualReplenishmentLeadSizesRecoveryAndAddsIndependentDirectUse() {
        var food = new SupplyPolicy(500, 4, 8, 10, 500, true);
        var material = new SupplyPolicy(500, 4, 8, 10, 500, false);
        var stocks = Map.of("food", new SupplyPlanner.Stock(0, 0, 0, 0, 0, 1, 10, food),
                "grain", new SupplyPlanner.Stock(100, 0, 0, 0, 0, 0, 1, material),
                "fuel", new SupplyPlanner.Stock(0, 0, 0, 0, 0, 0, 1, material));
        var needs = Map.of("food", new SupplyReservePlanner.Need(SupplyReservePlanner.Rate.of(1, 10), 0),
                "grain", new SupplyReservePlanner.Need(SupplyReservePlanner.Rate.ZERO, 0),
                "fuel", new SupplyReservePlanner.Need(SupplyReservePlanner.Rate.ZERO, 1));
        var flows = List.of(new SupplyReservePlanner.Flow("food", Map.of("grain", SupplyReservePlanner.Rate.of(3, 1),
                "fuel", SupplyReservePlanner.Rate.of(1, 8))));
        var timing = Map.of("food", new SupplyReservePlanner.Timing(100, SupplyReservePlanner.TimingBasis.LOCAL_ESTIMATE),
                "grain", new SupplyReservePlanner.Timing(100, SupplyReservePlanner.TimingBasis.LOCAL_ESTIMATE),
                "fuel", new SupplyReservePlanner.Timing(100, SupplyReservePlanner.TimingBasis.DELIVERY_ESTIMATE));
        var reserves = new SupplyReservePlanner().plan(needs, flows, timing, new ReservePolicy(10, 0));
        var oven = new SupplyPlanner.Process("oven", "food", 1, Map.of("grain", 3),
                1, Map.of("fuel", 8L), 0, 1, 1, 100);
        var input = new EconomicSnapshot(100, stocks, needs, flows, reserves, List.of(oven), List.of(), SourcingMarket.none(),
                Map.of("food", 4L, "grain", 1L, "fuel", 2L), List.of(), 0);
        var plan = new EconomicPlanner().domestic(input, new EconomicPlanningRules(2, 2, 100)).plan();
        assertEquals(4, plan.require("fuel").purchase(), "three fuel for twenty food plus one for another direct service");
        assertEquals(4L, plan.economy().orElseThrow().domesticTargets().get("fuel"));
        assertTrue(plan.require("fuel").urgentPurchase() >= 2,
                "recovery must bridge the real hundred-tick delivery, not the old ten-tick initial estimate");
        assertEquals(0, plan.require("food").production(), "unsecured fuel cannot become confirmed food");
    }
}
