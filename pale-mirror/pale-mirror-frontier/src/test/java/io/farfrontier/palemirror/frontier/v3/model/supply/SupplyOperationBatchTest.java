package io.farfrontier.palemirror.frontier.v3.model.supply;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SupplyOperationBatchTest {
    @Test void partialAndFullOperationsShareWorkButNotMaterialsAndAnotherOperationCostsMore() {
        var process = new SupplyPlanner.Process("station", "bread", 1, Map.of("wheat", 3),
                80, Map.of("coal", 1600L), 1600, 1200, 2400, 512, 1200, Map.of("worker", 2400L), 21);
        assertEquals(1200, process.labourTicks(1));
        assertEquals(1200, process.labourTicks(20));
        assertEquals(1200, process.completionTicks(20));
        assertEquals(2400, process.labourTicks(22));
        assertEquals(3600, process.completionTicks(22));
        assertEquals(21, process.recipesForLabour(1200));
        assertEquals(0, process.recipesForLabour(1199));
        assertEquals(0, process.labourTicks(0));
        var valuation = SourcingValuation.processes(List.of(process), Map.of("bread", 100L, "wheat", 1L, "coal", 20L), List.of(), 1);
        assertEquals(62, valuation.get("station")); // ceil(1200/21) labour + 3 grain + 1 heat share
    }
}
