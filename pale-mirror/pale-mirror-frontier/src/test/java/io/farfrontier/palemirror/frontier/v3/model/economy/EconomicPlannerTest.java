package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EconomicPlannerTest {
    @Test void cashForecastReconsidersImportsWhenOwnInputsOfferASustainableAlternative() {
        var base = facts(20, 60, 0, 15, List.of(oven()), List.of());
        var market = new SourcingMarket(15, 100, Map.of("oven", 4L),
                List.of(new SourcingMarket.Offer("food", "food", 20, 1, 1, 0, 0),
                        new SourcingMarket.Offer("fuel", "fuel", 2, 5, 5, 0, 0)));
        var input = new EconomicSnapshot(base.dayTicks(), base.stocks(), base.needs(), base.flows(), base.reserves(),
                base.processes(), base.commitments(), market, base.replacementPrices(), List.of(), 0);
        var plan = new EconomicPlanner().plan(input, RULES);
        assertEquals(0L, plan.preferredPurchaseQuantities().getOrDefault("food", 0L),
                "cheap food cannot exhaust the purse and strand existing ingredients");
        assertTrue(plan.economy().orElseThrow().fundedRestarts().contains("fuel"));
        assertTrue(plan.economy().orElseThrow().purchaseBudgets().values().stream().mapToLong(Long::longValue).sum() <= 15);
    }
    @Test void unfuelledWishDoesNotReserveWorkersAgainstFeasibleExportProduction() {
        var bakery = new SupplyPlanner.Process("oven", "food", 1, Map.of("grain", 3), 1, Map.of("fuel", 20L),
                0, 10, 1, 100, 1, Map.of("shared-worker", 40L));
        var field = new SupplyPlanner.Process("field", "grain", 1, Map.of(), 0, Map.of(), 0,
                10, 1, 20, 1, Map.of("shared-worker", 40L));
        var input = facts(0, 200, 0, 0, List.of(bakery, field),
                List.of(new EconomicSnapshot.ExportInterest("buyer", "grain", 220, 2, 1000, 1000, 0)));
        var plan = new EconomicPlanner().plan(input, RULES);
        assertEquals(0L, plan.backedRecipes().getOrDefault("oven", 0L));
        assertTrue(plan.economy().orElseThrow().exports().stream().anyMatch(e ->
                e.item().equals("grain") && e.additionalProduction() > 0),
                "missing fuel is not a reservation of otherwise usable workers");
        assertEquals(0, plan.economy().orElseThrow().purchaseBudgets().values().stream().mapToLong(Long::longValue).sum());
    }
    @Test void solventSettlementMayOfferUpperReservesWhileProtectingReplenishmentInterval() {
        var plan = new EconomicPlanner().plan(facts(30, 90, 2, 10000, List.of(oven()), List.of()), RULES);
        var economy = plan.economy().orElseThrow();
        assertEquals(0, economy.unfundedRequirements());
        assertTrue(economy.saleableStock().get("food") > 0, "cash distress is not required for lawful export");
        assertTrue(30 - economy.saleableStock().get("food") >= plan.reserves().get("food").protectedQuantity());
        var poor = new EconomicPlanner().plan(facts(5, 90, 2, 10000, List.of(oven()), List.of()), RULES);
        assertEquals(0, poor.economy().orElseThrow().saleableStock().get("food"));
    }
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
    @Test void actualSupplierQuoteFundsEvenLateRecoveryWithoutInventingTimelySupply() {
        var original = facts(0, 200, 0, 6, List.of(oven()), List.of());
        var market = new SourcingMarket(6, 100, Map.of("oven", 4L),
                List.of(new SourcingMarket.Offer("coal-supplier", "fuel", 20, 5, 5, 0, 0),
                        new SourcingMarket.Offer("cheap-but-distant", "fuel", 20, 1, 1, 0, 10, 10)));
        var input = new EconomicSnapshot(original.dayTicks(), original.stocks(), original.needs(), original.flows(),
                original.reserves(), original.processes(), original.commitments(), market, original.replacementPrices(), List.of(), 0);
        var plan = new EconomicPlanner().plan(input, RULES).economy().orElseThrow();
        assertEquals(5, plan.purchasePrices().get("fuel"), "fund the affordable delivered restart, not the cheapest sticker price");
        assertTrue(plan.fundedRestarts().contains("fuel"));
        var late = new SourcingMarket(6, 100, Map.of("oven", 4L),
                List.of(new SourcingMarket.Offer("coal-supplier", "fuel", 20, 5, 5, 10000, 0)));
        var delayed = new EconomicSnapshot(input.dayTicks(), input.stocks(), input.needs(), input.flows(), input.reserves(),
                input.processes(), input.commitments(), late, input.replacementPrices(), List.of(), 0);
        var recovery = new EconomicPlanner().plan(delayed, RULES);
        assertTrue(recovery.economy().orElseThrow().fundedRestarts().contains("fuel"),
                "late recovery still needs protected financing");
        assertEquals(0, recovery.require("food").production(), "a quote is not delivered fuel or secured bread");
        assertTrue(recovery.require("fuel").deadlineTicks() < 10000, "funding cannot postpone the real deadline");
        var expensiveTimely = new SourcingMarket(6, 100, Map.of("oven", 4L),
                List.of(new SourcingMarket.Offer("timely-unaffordable", "fuel", 20, 100, 100, 0, 0),
                        new SourcingMarket.Offer("late-affordable", "fuel", 20, 5, 5, 10000, 0)));
        var alternatives = new EconomicSnapshot(input.dayTicks(), input.stocks(), input.needs(), input.flows(), input.reserves(),
                input.processes(), input.commitments(), expensiveTimely, input.replacementPrices(), List.of(), 0);
        var fallback = new EconomicPlanner().plan(alternatives, RULES).economy().orElseThrow();
        assertTrue(fallback.fundedRestarts().contains("fuel"));
        assertEquals(5, fallback.purchasePrices().get("fuel"));
        var absent = new SourcingMarket(6, 100, Map.of("oven", 4L), List.of());
        var unavailable = new EconomicSnapshot(input.dayTicks(), input.stocks(), input.needs(), input.flows(), input.reserves(),
                input.processes(), input.commitments(), absent, input.replacementPrices(), List.of(), 0);
        assertFalse(new EconomicPlanner().plan(unavailable, RULES).economy().orElseThrow().fundedRestarts().contains("fuel"));
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
                List.of(new EconomicSnapshot.ExportInterest("neighbour", "grain", 100, 1, 100, 100, 0)));
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
                new EconomicSnapshot.ExportInterest("neighbour", "grain", 100, 1, 30, 30, 0),
                new EconomicSnapshot.ExportInterest("neighbour", "fuel", 100, 1, 30, 30, 0)));
        var exports = new EconomicPlanner().plan(input, RULES).economy().orElseThrow().exports();
        assertEquals(30, exports.stream().mapToLong(EconomicPlan.Export::additionalProduction).sum());
        assertEquals(30, exports.stream().mapToLong(EconomicPlan.Export::conditionalRevenue).sum());
    }
    @Test void exportAdviceUsesCommodityFundingIncludingFreightWithoutReallocatingDomesticMoney() {
        var mine = new SupplyPlanner.Process("mine", "fuel", 1, Map.of(), 0, Map.of(), 0,
                10, 1, 100, 1, Map.of("worker", 100L));
        var input = facts(100, 0, 0, 0, List.of(oven(), mine), List.of());
        var planner = new EconomicPlanner();
        var domestic = planner.domestic(input, RULES);
        var complete = planner.withExports(domestic, List.of(
                new EconomicSnapshot.ExportInterest("neighbour", "fuel", 100, 1, 1000, 30, 2)));
        var export = complete.economy().orElseThrow().exports().getFirst();
        assertEquals(10, export.additionalProduction(), "goods and freight share the 30-unit commodity budget");
        assertEquals(10, export.conditionalRevenue(), "freight is not the goods seller's proceeds");
        assertEquals(domestic.plan().demands(), complete.demands());
        assertEquals(domestic.plan().economy().orElseThrow().purchaseBudgets(),
                complete.economy().orElseThrow().purchaseBudgets());
        assertEquals(domestic.plan().economy().orElseThrow().checkpoints(),
                complete.economy().orElseThrow().checkpoints(), "conditional receipts cannot fund domestic purchases");
    }
    @Test void liquidationDoesNotAuthorizeNewExportProductionBelowItsOpportunityCost() {
        var mine = new SupplyPlanner.Process("mine", "fuel", 1, Map.of(), 0, Map.of(), 0,
                10, 1, 7, 134, Map.of("miner", 48000L));
        var original = facts(100, 0, 7, 0, List.of(oven(), mine),
                List.of(new EconomicSnapshot.ExportInterest("neighbour", "fuel", 7, 25, 175, 175, 0)));
        var input = new EconomicSnapshot(original.dayTicks(), original.stocks(), original.needs(), original.flows(),
                original.reserves(), original.processes(), original.commitments(),
                new SourcingMarket(0, 100, Map.of("oven", 4L, "mine", 134L), List.of()),
                original.replacementPrices(), original.exportInterest(), 0);
        var plan = new EconomicPlanner().plan(input, RULES).economy().orElseThrow();
        assertEquals(0, plan.additionalProduction().get("fuel"));
        assertEquals(7, plan.exports().getFirst().existingStock());
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
                new EconomicSnapshot.ExportInterest("neighbour", "grain", 100, 2, 1000, 1000, 0),
                new EconomicSnapshot.ExportInterest("neighbour", "fuel", 100, 5, 1000, 1000, 0)));
        var plan = new EconomicPlanner().plan(input, RULES).economy().orElseThrow();
        assertEquals(50, plan.additionalProduction().get("grain"));
        assertEquals(0, plan.additionalProduction().get("fuel"));
    }
    @Test void spareLabourDoesNotAuthorizeDestroyingMoreValuableIngredients() {
        var input = facts(100, 200, 20, 0, List.of(oven()),
                List.of(new EconomicSnapshot.ExportInterest("neighbour", "food", 100, 2, 200, 200, 0)));
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
