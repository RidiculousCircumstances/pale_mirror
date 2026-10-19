package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementBudget;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EconomicPurchaseFundingTest {
    @Test void immediateNeedCanUseUnbackedRecoveryEnvelopeButNotInventCashOrSpendBackedRestart() {
        var budgets = Map.of("grain", 128L, "food", 0L);
        var plan = new EconomicPlan(96000, budgets, budgets, budgets, 0, 0, 0, List.of(), List.of(), Set.of(),
                Map.of("grain", 1L, "food", 3L), Set.of(), Map.of("grain", 0L, "food", 0L));
        var needs = List.of(new ProcurementBudget.Need("grain", 566, new FixedScalar(1), 0, 2),
                new ProcurementBudget.Need("food", 189, new FixedScalar(3), 0));
        assertEquals(0, EconomicPurchaseFunding.available(plan, new FixedScalar(128), needs, "food").raw());
        assertEquals(63, EconomicPurchaseFunding.available(plan, new FixedScalar(128), needs, "food", 21).raw());
        assertEquals(5, EconomicPurchaseFunding.available(plan, new FixedScalar(5), needs, "food", 21).raw());
        var backed = new EconomicPlan(96000, budgets, budgets, budgets, 0, 0, 0, List.of(), List.of(), Set.of(),
                plan.purchasePrices(), Set.of("grain"), plan.saleableStock());
        assertEquals(3, EconomicPurchaseFunding.available(backed, new FixedScalar(5), needs, "food", 21).raw());
    }
    @Test void unselectedEarlierFinishedGoodsCannotVetoTheirFundedProductionInputs() {
        var budgets = Map.of("input", 4L, "output", 0L);
        var plan = new EconomicPlan(96000, budgets, budgets, budgets, 0, 0, 0, List.of(), List.of(), Set.of(),
                Map.of("input", 1L, "output", 4L), Set.of(), Map.of("input", 0L, "output", 0L));
        var needs = List.of(new ProcurementBudget.Need("output", 200, new FixedScalar(4), 0),
                new ProcurementBudget.Need("input", 500, FixedScalar.ONE, 100));
        assertEquals(30, EconomicPurchaseFunding.available(plan, new FixedScalar(30), needs, "input").raw());
        var allocated = Map.of("input", 4L, "output", 8L);
        var withEmergencyPurchase = new EconomicPlan(96000, budgets, budgets, allocated, 0, 0, 0,
                List.of(), List.of(), Set.of(), plan.purchasePrices(), Set.of(), plan.saleableStock());
        assertEquals(22, EconomicPurchaseFunding.available(withEmergencyPurchase, new FixedScalar(30), needs, "input").raw(),
                "an actual earlier allocation stays protected, not the whole hypothetical reserve");
        assertEquals(0, EconomicPurchaseFunding.available(withEmergencyPurchase, new FixedScalar(5), needs, "input").raw(),
                "money already spent elsewhere cannot be promised again");
    }
    @Test void restartCanUseAnAffordableAlternativeWithoutSpendingItsComplementaryInputBudget() {
        var budgets = Map.of("coal", 2L, "wheat", 6L, "bread", 26L);
        var plan = new EconomicPlan(96000, budgets, budgets, budgets, 0, 0, 0, List.of(), List.of(), Set.of(),
                Map.of("coal", 5L, "wheat", 2L, "bread", 4L), Set.of("coal", "wheat"), Map.of("coal", 0L, "wheat", 0L, "bread", 0L));
        var needs = List.of(new ProcurementBudget.Need("coal", 1, new FixedScalar(5), 0, 1),
                new ProcurementBudget.Need("wheat", 3, new FixedScalar(2), 0, 3),
                new ProcurementBudget.Need("bread", 20, new FixedScalar(4), 0));
        assertEquals(28, EconomicPurchaseFunding.available(plan, new FixedScalar(34), needs, "coal").raw());
        assertEquals(4, EconomicPurchaseFunding.available(plan, new FixedScalar(10), needs, "coal").raw());
        assertEquals(23, EconomicPurchaseFunding.available(plan, new FixedScalar(34), needs, "bread").raw());
        assertEquals(0, EconomicPurchaseFunding.available(plan, new FixedScalar(34), needs, "stone").raw());
    }
}
