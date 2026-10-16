package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcurementBudgetTest {
    @Test void equalOverdueNeedsKeepACompleteAffordableRestartInsteadOfSpendingEverythingOnFallback() {
        var needs = List.of(new ProcurementBudget.Need("bread", 80, FixedScalar.whole(3), 0),
                new ProcurementBudget.Need("fuel", 5, FixedScalar.whole(8), 0, 1),
                new ProcurementBudget.Need("grain", 180, FixedScalar.ONE, 0, 3));
        assertEquals(FixedScalar.whole(1), ProcurementBudget.forPurchase(FixedScalar.whole(12), needs, "bread"));
        assertEquals(FixedScalar.whole(9), ProcurementBudget.forPurchase(FixedScalar.whole(12), needs, "fuel"));
        assertEquals(FixedScalar.whole(4), ProcurementBudget.forPurchase(FixedScalar.whole(12), needs, "grain"));
        assertTrue(ProcurementBudget.fundsRestart(FixedScalar.whole(12), needs, "fuel"));
        assertEquals(FixedScalar.whole(4), ProcurementBudget.forPurchase(FixedScalar.whole(4), needs, "bread"),
                "an unaffordable restart must not forbid an affordable meal");
        assertFalse(ProcurementBudget.fundsRestart(FixedScalar.whole(4), needs, "fuel"));
    }
    @Test void earlierFoodDependencyIsProtectedFromReservePurchasesAndOptionalTravelMoney() {
        var needs = List.of(new ProcurementBudget.Need("fuel", 8, FixedScalar.whole(2), 100),
                new ProcurementBudget.Need("stone", 128, FixedScalar.whole(1), Long.MAX_VALUE));
        assertEquals(FixedScalar.whole(20), ProcurementBudget.forPurchase(FixedScalar.whole(20), needs, "fuel"));
        assertEquals(FixedScalar.whole(4), ProcurementBudget.forPurchase(FixedScalar.whole(20), needs, "stone"));
        assertEquals(FixedScalar.ZERO, ProcurementBudget.forOptionalSpending(FixedScalar.whole(20), needs));
        assertEquals(FixedScalar.whole(6), ProcurementBudget.forOptionalSpending(FixedScalar.whole(150), needs));
    }
    @Test void noOverflowOrMoneyCreationAndNoPermanentHoldWhenDemandEnds() {
        var large = List.of(new ProcurementBudget.Need("input", Long.MAX_VALUE, FixedScalar.whole(2), 1));
        assertEquals(FixedScalar.ZERO, ProcurementBudget.forOptionalSpending(FixedScalar.whole(20), large));
        assertEquals(FixedScalar.whole(20), ProcurementBudget.forOptionalSpending(FixedScalar.whole(20), List.of()));
    }
}
