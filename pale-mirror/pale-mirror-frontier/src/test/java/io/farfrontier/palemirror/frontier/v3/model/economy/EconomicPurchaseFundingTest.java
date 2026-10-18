package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementBudget;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EconomicPurchaseFundingTest {
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
