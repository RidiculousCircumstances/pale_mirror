package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EconomicPlannerTest {
    private static final EconomicPlanningRules RULES = new EconomicPlanningRules(4, 4, 256);
    private static final SupplyPolicy ORDINARY = new SupplyPolicy(1000, 1, 64, 100, 500, false);
    private static final SupplyPolicy ESSENTIAL = new SupplyPolicy(1000, 1, 64, 100, 500, true);
    private static SupplyPlanner.Stock stock(String item, long held) {
        return new SupplyPlanner.Stock(held, 0, 0, 100, 0, item.equals("food") ? 1 : 0, 10,
                item.equals("food") ? ESSENTIAL : ORDINARY);
    }
    private static EconomicSnapshot facts(long food, long grain, long fuel, long cash,
            List<SupplyPlanner.Process> processes, List<EconomicSnapshot.ExportInterest> interests) {
        var stocks = Map.of("food", stock("food", food), "grain", stock("grain", grain), "fuel", stock("fuel", fuel));
        var needs = Map.of("food", new SupplyReservePlanner.Need(SupplyReservePlanner.Rate.of(1, 10), 0),
                "grain", new SupplyReservePlanner.Need(SupplyReservePlanner.Rate.ZERO, 0),
                "fuel", new SupplyReservePlanner.Need(SupplyReservePlanner.Rate.ZERO, 0));
        var flows = List.of(new SupplyReservePlanner.Flow("food", Map.of("grain", SupplyReservePlanner.Rate.of(3, 1),
                "fuel", SupplyReservePlanner.Rate.of(1, 20))));
        var timing = new SupplyReservePlanner.Timing(100, SupplyReservePlanner.TimingBasis.LOCAL_ESTIMATE);
        var reserves = new SupplyReservePlanner().plan(needs, flows, Map.of("food", timing, "grain", timing, "fuel", timing), new ReservePolicy(10, 500));
        return new EconomicSnapshot(100, stocks, needs, flows, reserves, processes, List.of(),
                new SourcingMarket(cash, 100, Map.of("oven", 4L, "field", 1L, "mine", 1L), List.of()),
                Map.of("food", 4L, "grain", 1L, "fuel", 2L), interests, 0);
    }
    private static SupplyPlanner.Process oven() {
        return new SupplyPlanner.Process("oven", "food", 1, Map.of("grain", 3), 1, Map.of("fuel", 20L),
                0, 10, 1, 100, 1);
    }
    @Test void storedFinishedGoodsOffsetInputReservesInsteadOfSpendingTreasuryTwice() {
        var facts = facts(100, 0, 0, 100, List.of(oven()), List.of());
        var plan = new EconomicPlanner().plan(facts, RULES);
        assertEquals(0, plan.require("grain").purchase());
        assertEquals(0, plan.require("fuel").purchase());
        assertEquals(0, plan.economy().orElseThrow().domesticTargets().get("grain"));
        assertEquals(0, plan.economy().orElseThrow().purchaseBudgets().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(100, facts.market().availableMoney(), "advice cannot debit actual cash");
    }
    @Test void grainRichFuelPoorSettlementFundsComplementaryInputNotAnotherGrainBuffer() {
        var plan = new EconomicPlanner().plan(facts(0, 200, 0, 10, List.of(oven()), List.of()), RULES);
        var economic = plan.economy().orElseThrow();
        assertTrue(plan.require("fuel").purchase() >= 1);
        assertTrue(economic.purchaseBudgets().get("fuel") >= 2);
        assertEquals(0, economic.purchaseBudgets().get("grain"));
        assertTrue(economic.purchaseBudgets().values().stream().mapToLong(Long::longValue).sum() <= 10);
        assertTrue(plan.require("food").recoveryProduction() > 0);
        assertEquals(0, plan.require("food").production(), "missing fuel is not secured bread");
    }
    @Test void exportOpportunityUsesSpareCapacityButNeverBecomesAvailableMoney() {
        var field = new SupplyPlanner.Process("field", "grain", 1, Map.of(), 0, Map.of(), 0,
                10, 1, 100, 1, Map.of("worker", 50L));
        var input = facts(100, 0, 0, 0, List.of(oven(), field),
                List.of(new EconomicSnapshot.ExportInterest("neighbour", "grain", 100, 1, 100)));
        var plan = new EconomicPlanner().plan(input, RULES).economy().orElseThrow();
        assertEquals(50, plan.productionTargets().get("grain"));
        assertEquals(50, plan.exports().getFirst().conditionalRevenue());
        assertEquals(0, plan.purchaseBudgets().values().stream().mapToLong(Long::longValue).sum());
        assertTrue(plan.constraints().contains(EconomicPlan.Constraint.CONDITIONAL_EXPORT_INCOME));
    }
    @Test void competingExportProductsShareWorkerAndBuyerBudgets() {
        var field = new SupplyPlanner.Process("field", "grain", 1, Map.of(), 0, Map.of(), 0, 10, 1, 100, 1, Map.of("worker", 50L));
        var mine = new SupplyPlanner.Process("mine", "fuel", 1, Map.of(), 0, Map.of(), 0, 10, 1, 100, 1, Map.of("worker", 50L));
        var input = facts(100, 0, 0, 0, List.of(oven(), field, mine), List.of(
                new EconomicSnapshot.ExportInterest("neighbour", "grain", 100, 1, 30),
                new EconomicSnapshot.ExportInterest("neighbour", "fuel", 100, 1, 30)));
        var exports = new EconomicPlanner().plan(input, RULES).economy().orElseThrow().exports();
        assertEquals(30, exports.stream().mapToLong(EconomicPlan.Export::additionalProduction).sum());
        assertEquals(30, exports.stream().mapToLong(EconomicPlan.Export::conditionalRevenue).sum());
    }
    @Test void changedFactsReplanAndUntimedPromisesDoNotEraseDatedShortages() {
        var original = facts(0, 0, 0, 0, List.of(oven()), List.of());
        var stocks = new HashMap<>(original.stocks());
        stocks.put("food", new SupplyPlanner.Stock(0, 100, 100, 100, 0, 1, 10, ESSENTIAL));
        var input = new EconomicSnapshot(original.dayTicks(), stocks, original.needs(), original.flows(), original.reserves(),
                original.processes(), original.commitments(), original.market(), original.replacementPrices(), List.of(), 500);
        var plan = new EconomicPlanner().plan(input, RULES).economy().orElseThrow();
        assertTrue(plan.checkpoints().getLast().netRequirements().get("food") > 0);
        assertEquals(500, plan.contractReceivables());
        assertEquals(0, plan.purchaseBudgets().values().stream().mapToLong(Long::longValue).sum());
        assertTrue(plan.checkpoints().getLast().cashWithoutNewSales() < 0);
        assertEquals(new EconomicPlanner().plan(input, RULES), new EconomicPlanner().plan(input, RULES));
    }
    @Test void economicalImportsDoNotAlsoStartOwnProductionForTheSameReserve() {
        var field = new SupplyPlanner.Process("field", "grain", 1, Map.of(), 0, Map.of(), 0, 10, 1, 100, 1);
        var original = facts(20, 0, 2, 500, List.of(oven(), field), List.of());
        var market = new SourcingMarket(500, 100, Map.of("oven", 4L, "field", 10L),
                List.of(new SourcingMarket.Offer("neighbour", "grain", 200, 1, 1, 0, 0)));
        var input = new EconomicSnapshot(original.dayTicks(), original.stocks(), original.needs(), original.flows(),
                original.reserves(), original.processes(), original.commitments(), market, original.replacementPrices(), List.of(), 0);
        var plan = new EconomicPlanner().plan(input, RULES);
        assertTrue(plan.preferredPurchaseSpending().get("grain") > 0);
        assertEquals(0, plan.require("grain").production());
        assertEquals(0, plan.economy().orElseThrow().productionTargets().get("grain"));
        assertTrue(plan.economy().orElseThrow().domesticTargets().get("grain") > 0);
    }
    @Test void chosenFinishedFoodPurchaseDoesNotAlsoPurchaseItsRecipeAndForecastUsesItsPrice() {
        var original = facts(20, 0, 0, 500, List.of(oven()), List.of());
        var market = new SourcingMarket(500, 100, Map.of("oven", 10L),
                List.of(new SourcingMarket.Offer("finished-food", "food", 100, 3, 3, 0, 0)));
        var input = new EconomicSnapshot(original.dayTicks(), original.stocks(), original.needs(), original.flows(),
                original.reserves(), original.processes(), original.commitments(), market, original.replacementPrices(), List.of(), 0);
        var plan = new EconomicPlanner().plan(input, RULES); var economy = plan.economy().orElseThrow();
        assertEquals(20, plan.preferredPurchaseQuantities().get("food"));
        assertEquals(0, plan.require("grain").purchase()); assertEquals(0, plan.require("fuel").purchase());
        assertEquals(0, economy.productionTargets().get("grain"));
        assertEquals(0, economy.purchaseBudgets().get("fuel"));
        assertEquals(60, economy.checkpoints().getLast().importCost());
        assertEquals(440, economy.checkpoints().getLast().cashWithoutNewSales());
        assertEquals(20, economy.checkpoints().getLast().netRequirements().get("food"), "an intended purchase is not already delivered food");
    }
}
