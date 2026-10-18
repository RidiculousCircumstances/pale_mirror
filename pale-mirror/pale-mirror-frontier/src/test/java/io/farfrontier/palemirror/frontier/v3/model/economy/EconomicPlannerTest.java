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
    @Test void unbackedGrainReserveCanFinanceMissingFuelWithoutSellingTheRestartIngredients() {
        var plan = new EconomicPlanner().plan(facts(0, 90, 0, 0, List.of(oven()), List.of()), RULES);
        var economy = plan.economy().orElseThrow();
        assertTrue(economy.domesticTargets().get("grain") >= 90);
        assertTrue(economy.saleableStock().get("grain") > 0, "desired reserve is not an unconditional export ban");
        assertTrue(90 - economy.saleableStock().get("grain") >= 3, "retain ingredients for the backed restart boundary");
        assertEquals(0, economy.purchaseBudgets().values().stream().mapToLong(Long::longValue).sum(),
                "possible liquidation proceeds cannot be spent before sale");
    }
    @Test void actualSupplierQuoteFundsRestartButAnUnreachableOrLateOfferDoesNot() {
        var original = facts(0, 200, 0, 6, List.of(oven()), List.of());
        var market = new SourcingMarket(6, 100, Map.of("oven", 4L),
                List.of(new SourcingMarket.Offer("coal-supplier", "fuel", 20, 5, 5, 0, 0)));
        var input = new EconomicSnapshot(original.dayTicks(), original.stocks(), original.needs(), original.flows(),
                original.reserves(), original.processes(), original.commitments(), market, original.replacementPrices(), List.of(), 0);
        var plan = new EconomicPlanner().plan(input, RULES).economy().orElseThrow();
        assertEquals(5, plan.purchasePrices().get("fuel"));
        assertTrue(plan.fundedRestarts().contains("fuel"));
        var late = new SourcingMarket(6, 100, Map.of("oven", 4L),
                List.of(new SourcingMarket.Offer("coal-supplier", "fuel", 20, 5, 5, 10000, 0)));
        var delayed = new EconomicSnapshot(input.dayTicks(), input.stocks(), input.needs(), input.flows(), input.reserves(),
                input.processes(), input.commitments(), late, input.replacementPrices(), List.of(), 0);
        assertFalse(new EconomicPlanner().plan(delayed, RULES).economy().orElseThrow().fundedRestarts().contains("fuel"));
    }
    @Test void forecastShortfallOffersOptionalFoodReserveBeforeTheNextRestartFails() {
        var input = facts(20, 3, 2, 3, List.of(oven()), List.of());
        var plan = new EconomicPlanner().plan(input, RULES);
        var economy = plan.economy().orElseThrow();
        assertTrue(economy.unfundedRequirements() > 0);
        assertTrue(economy.saleableStock().get("food") > 0);
        assertTrue(20 - economy.saleableStock().get("food") >= plan.reserves().get("food").protectedQuantity());
        assertEquals(0, economy.saleableStock().get("grain"), "do not sell the next recipe's ingredients");
        assertTrue(economy.purchaseBudgets().values().stream().mapToLong(Long::longValue).sum() <= 3);
        assertEquals(3, input.market().availableMoney(), "offers cannot credit the treasury");
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
        assertEquals(50, plan.additionalProduction().get("grain"));
        assertEquals(50, plan.exports().getFirst().conditionalRevenue());
        assertEquals(0, plan.purchaseBudgets().values().stream().mapToLong(Long::longValue).sum());
        assertTrue(plan.constraints().contains(EconomicPlan.Constraint.CONDITIONAL_EXPORT_INCOME));

        long horizon = plan.horizonTicks();
        var lateField = new SupplyPlanner.Process("field", "grain", 1, Map.of(), 0, Map.of(), 0,
                horizon + 1, 1, 100, 1, Map.of("worker", 50L), 1,
                List.of(new SupplyPlanner.OutputRelease(horizon + 1, 100)));
        var late = new EconomicPlanner().plan(facts(100, 7, 0, 0, List.of(oven(), lateField),
                input.exportInterest()), RULES).economy().orElseThrow();
        assertEquals(7, late.exports().getFirst().existingStock(), "already stored goods remain saleable");
        assertEquals(0, late.exports().getFirst().additionalProduction(), "next wave misses this plan's horizon");
        assertEquals(7, late.exports().getFirst().conditionalRevenue(), "late output cannot inflate expected receipts");

        var datedField = new SupplyPlanner.Process("field", "grain", 1, Map.of(), 0, Map.of(), 0,
                horizon, 1, 100, 1, Map.of("worker", 50L), 1,
                List.of(new SupplyPlanner.OutputRelease(horizon, 12), new SupplyPlanner.OutputRelease(horizon + 1, 100)));
        var dated = new EconomicPlanner().plan(facts(100, 7, 0, 0, List.of(oven(), datedField),
                input.exportInterest()), RULES).economy().orElseThrow();
        assertEquals(12, dated.exports().getFirst().additionalProduction(), "include only released capacity, with inclusive horizon");
        assertEquals(19, dated.exports().getFirst().conditionalRevenue());
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
    @Test void idlePublicCapacityCanEarnBelowTheSourcingShadowWageWithoutInventingCash() {
        var mine = new SupplyPlanner.Process("mine", "fuel", 1, Map.of(), 0, Map.of(), 0,
                10, 1, 7, 134, Map.of("miner", 48000L));
        var original = facts(100, 0, 0, 0, List.of(oven(), mine),
                List.of(new EconomicSnapshot.ExportInterest("neighbour", "fuel", 7, 25, 175)));
        var input = new EconomicSnapshot(original.dayTicks(), original.stocks(), original.needs(), original.flows(),
                original.reserves(), original.processes(), original.commitments(),
                new SourcingMarket(0, 100, Map.of("oven", 4L, "mine", 134L), List.of()),
                original.replacementPrices(), original.exportInterest(), 0);
        var plan = new EconomicPlanner().plan(input, RULES).economy().orElseThrow();
        assertEquals(7, plan.additionalProduction().get("fuel"));
        assertEquals(175, plan.exports().getFirst().conditionalRevenue());
        assertEquals(0, plan.purchaseBudgets().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(0, input.market().availableMoney());
    }
    @Test void competingExportsPreferContributionPerWorkerTickNotTheHighestStickerPrice() {
        var field = new SupplyPlanner.Process("field", "grain", 1, Map.of(), 0, Map.of(), 0,
                10, 1, 100, 1, Map.of("worker", 50L));
        var mine = new SupplyPlanner.Process("mine", "fuel", 1, Map.of(), 0, Map.of(), 0,
                10, 1, 100, 10, Map.of("worker", 50L));
        var input = facts(100, 0, 0, 0, List.of(oven(), field, mine), List.of(
                new EconomicSnapshot.ExportInterest("neighbour", "grain", 100, 1, 1000),
                new EconomicSnapshot.ExportInterest("neighbour", "fuel", 100, 5, 1000)));
        var plan = new EconomicPlanner().plan(input, RULES).economy().orElseThrow();
        assertEquals(50, plan.additionalProduction().get("grain"));
        assertEquals(0, plan.additionalProduction().get("fuel"));
    }
    @Test void spareLabourDoesNotAuthorizeDestroyingMoreValuableIngredients() {
        var input = facts(100, 200, 20, 0, List.of(oven()),
                List.of(new EconomicSnapshot.ExportInterest("neighbour", "food", 100, 2, 200)));
        var plan = new EconomicPlanner().plan(input, RULES).economy().orElseThrow();
        assertEquals(0, plan.additionalProduction().get("food"));
        assertTrue(plan.exports().stream().allMatch(e -> e.additionalProduction() == 0));
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
        assertEquals(0, plan.economy().orElseThrow().additionalProduction().get("grain"));
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
        assertEquals(0, economy.additionalProduction().get("grain"));
        assertEquals(0, economy.purchaseBudgets().get("fuel"));
        assertEquals(60, economy.checkpoints().getLast().importCost());
        assertEquals(440, economy.checkpoints().getLast().cashWithoutNewSales());
        assertEquals(20, economy.checkpoints().getLast().netRequirements().get("food"), "an intended purchase is not already delivered food");
    }
}
