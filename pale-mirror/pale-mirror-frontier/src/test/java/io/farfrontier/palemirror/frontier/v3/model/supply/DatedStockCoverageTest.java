package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DatedStockCoverageTest {
    @Test void lateConfirmedDeliveryCannotHideStarvationBeforeArrival() {
        var need = new SupplyReservePlanner.Need(SupplyReservePlanner.Rate.of(1, 10), 0);
        assertFalse(DatedStockCoverage.assess(2, need, 1, List.of(new SupplyPlanner.Arrival(100, 80)), 100).covered());
        assertTrue(DatedStockCoverage.assess(2, need, 1, List.of(new SupplyPlanner.Arrival(100, 10)), 100).covered());
        assertFalse(DatedStockCoverage.assess(2, need, 1, List.of(new SupplyPlanner.Arrival(100, 101)), 100).covered());
    }
    @Test void presentDeliveryAndEqualTimePartitionsHaveTheSameCoverage() {
        var need = new SupplyReservePlanner.Need(SupplyReservePlanner.Rate.of(1, 10), 3);
        assertTrue(DatedStockCoverage.assess(0, need, 1, List.of(new SupplyPlanner.Arrival(14, 0)), 100).covered());
        assertEquals(DatedStockCoverage.assess(5, need, 1, List.of(new SupplyPlanner.Arrival(10, 10)), 100),
                DatedStockCoverage.assess(5, need, 1, List.of(new SupplyPlanner.Arrival(4, 10), new SupplyPlanner.Arrival(6, 10)), 100));
    }
}
