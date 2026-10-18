package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryCostTest {
    @Test void oneJourneyExpenseIsSharedByCargoAndNeverBecomesAnInternalWage() {
        var cost = new DeliveryCost(FixedScalar.whole(2), 20000);
        var small = cost.unitCharge(FixedScalar.ONE, FixedScalar.whole(2));
        var full = cost.unitCharge(FixedScalar.ONE, FixedScalar.whole(64));
        assertEquals(FixedScalar.ONE, small);
        assertEquals(new FixedScalar(31250), full);
        assertEquals(cost.resourceExpense(), full.multiply(64));
        assertEquals(cost.resourceExpense(), new DeliveryCost(cost.resourceExpense(), 40000).resourceExpense());
        assertEquals(FixedScalar.whole(2), cost.unitCharge(FixedScalar.whole(2), FixedScalar.whole(2)));
    }
}
