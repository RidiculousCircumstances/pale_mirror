package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EconomicRestartFundingTest {
    @Test void unavailableOrUnaffordableIndependentRepairCannotEraseFundedFoodRestart() {
        for (var stoneOffers : List.of(List.<SourcingMarket.Offer>of(), List.of(offer("stone", 100)))) {
            var offers = new ArrayList<>(stoneOffers); offers.add(offer("fuel", 2));
            var facts = facts(200, 6, Map.of("stone", 2), offers);
            var plan = plan(facts);
            assertEquals(Set.of("fuel"), plan.economy().orElseThrow().fundedRestarts());
            assertTrue(plan.economy().orElseThrow().purchaseBudgets().get("fuel") >= 2);
            assertEquals(0, plan.require("food").production(), "quoted fuel is not delivered fuel");
            assertEquals(6, facts.market().availableMoney(), "funding advice creates no hold or cash");
            assertTrue(plan.economy().orElseThrow().purchaseBudgets().values().stream().mapToLong(Long::longValue).sum() <= 6);
        }
    }
    @Test void affordableFuelAloneDoesNotFundAnIncompleteFoodRecipe() {
        var plan = plan(facts(0, 6, Map.of("stone", 2), List.of(offer("fuel", 2))));
        assertFalse(plan.economy().orElseThrow().fundedRestarts().contains("fuel"),
                "grain and fuel belong to the same restart, not independent purchases");
    }
    @Test void twoRestartsCannotReuseTheSameIngredientsOrCash() {
        var plan = plan(facts(3, 2, Map.of("grain", 3, "stone", 1),
                List.of(offer("fuel", 2), offer("stone", 1))));
        assertEquals(Set.of("fuel"), plan.economy().orElseThrow().fundedRestarts());
        assertEquals(2L, plan.economy().orElseThrow().purchaseBudgets().get("fuel"));
        assertEquals(0L, plan.economy().orElseThrow().purchaseBudgets().get("stone"));
        var stockLimited = plan(facts(3, 10, Map.of("grain", 3, "stone", 1),
                List.of(offer("fuel", 2), offer("stone", 1))));
        assertEquals(Set.of("fuel"), stockLimited.economy().orElseThrow().fundedRestarts(),
                "spare money cannot recreate grain already assigned to food");
    }
    @Test void smallRestartSharingAnInputWithLargeRepairRetainsItsOwnFunding() {
        var facts = facts(200, 2, Map.of("fuel", 10), List.of(offer("fuel", 2)));
        var plan = plan(facts);
        assertEquals(Set.of("fuel"), plan.economy().orElseThrow().fundedRestarts());
        assertEquals(2L, plan.economy().orElseThrow().purchaseBudgets().get("fuel"));
        assertTrue(plan.require("fuel").protectedInput() > 1, "the unrelated repair also needs this commodity");
    }
    @Test void buyingARecoveryInputAndManufacturingItRemainSeparateAlternatives() {
        var original = facts(200, 2, Map.of("stone", 1), List.of(offer("stone", 1)));
        var stocks = new TreeMap<>(original.stocks()); var old = stocks.get("tool");
        stocks.put("tool", new SupplyPlanner.Stock(0, 0, 0, old.target(), 0, 0, 1, old.policy(),
                List.of(), OptionalLong.empty(), Optional.of(new ProductionRequirement(1, 0))));
        var facts = new EconomicSnapshot(original.dayTicks(), stocks, original.needs(), original.flows(),
                original.reserves(), original.processes(), original.commitments(), original.market(),
                original.replacementPrices(), List.of(), 0);
        assertEquals(Set.of("stone"), plan(facts).economy().orElseThrow().fundedRestarts(),
                "the recipe cannot require its own finished output merely because work needs that output");
    }

    private static SupplyPlanner.Plan plan(EconomicSnapshot facts) {
        return new EconomicPlanner().plan(facts, new EconomicPlanningRules(4, 4, 256));
    }
    private static SourcingMarket.Offer offer(String item, long price) {
        return new SourcingMarket.Offer(item + "-supplier", item, 100, price, price, 0, 0);
    }
    private static EconomicSnapshot facts(long grain, long cash, Map<String, Integer> toolInputs,
            List<SourcingMarket.Offer> offers) {
        var ordinary = new SupplyPolicy(1000, 1, 64, 100, 500, false);
        var essential = new SupplyPolicy(1000, 1, 64, 100, 500, true);
        var stocks = new TreeMap<String, SupplyPlanner.Stock>();
        var needs = new TreeMap<String, SupplyReservePlanner.Need>();
        var prices = new TreeMap<String, Long>();
        for (String item : List.of("food", "grain", "fuel", "tool", "stone")) {
            stocks.put(item, new SupplyPlanner.Stock(item.equals("grain") ? grain : 0, 0, 0, 100, 0,
                    item.equals("food") ? 1 : 0, 10, item.equals("food") ? essential : ordinary));
            needs.put(item, new SupplyReservePlanner.Need(item.equals("food") ? SupplyReservePlanner.Rate.of(1, 10)
                    : SupplyReservePlanner.Rate.ZERO, item.equals("tool") ? 1 : 0));
            prices.put(item, 2L);
        }
        var oven = new SupplyPlanner.Process("oven", "food", 1, Map.of("grain", 3), 1,
                Map.of("fuel", 20L), 0, 10, 1, 100);
        var repair = new SupplyPlanner.Process("repair", "tool", 1, toolInputs, 0, Map.of(), 0, 10, 1, 1);
        var flowInputs = new TreeMap<String, SupplyReservePlanner.Rate>();
        toolInputs.forEach((item, quantity) -> flowInputs.put(item, SupplyReservePlanner.Rate.of(quantity, 1)));
        var flows = List.of(new SupplyReservePlanner.Flow("food", Map.of("grain", SupplyReservePlanner.Rate.of(3, 1),
                "fuel", SupplyReservePlanner.Rate.of(1, 20))), new SupplyReservePlanner.Flow("tool", flowInputs));
        var timings = new TreeMap<String, SupplyReservePlanner.Timing>();
        stocks.keySet().forEach(item -> timings.put(item, new SupplyReservePlanner.Timing(100, SupplyReservePlanner.TimingBasis.LOCAL_ESTIMATE)));
        var reserves = new SupplyReservePlanner().plan(needs, flows, timings, new ReservePolicy(10, 500));
        return new EconomicSnapshot(100, stocks, needs, flows, reserves, List.of(oven, repair), List.of(),
                new SourcingMarket(cash, 100, Map.of("oven", 100L, "repair", 100L), offers), prices, List.of(), 0);
    }
}
