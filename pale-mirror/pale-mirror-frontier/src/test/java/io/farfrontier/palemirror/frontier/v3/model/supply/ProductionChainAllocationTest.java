package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionChainAllocationTest {
    @Test void downstreamDemandIncludesFuelAndTransitiveIngredientsWithoutInventingUnrelatedUse() {
        var oven = new SupplyPlanner.Process("oven", "food", 1, Map.of("grain", 2), 1, Map.of("fuel", 10L), 0, 5, 1, 20, 3);
        var mill = new SupplyPlanner.Process("mill", "grain", 2, Map.of("crop", 1), 0, Map.of(), 0, 5, 1, 20, 3);
        var processes = List.of(oven, mill);
        assertEquals(Set.of("crop", "grain", "food"), ProductionReachability.consumers(processes, "crop"));
        assertEquals(Set.of("fuel", "food"), ProductionReachability.consumers(processes, "fuel"));
        assertEquals(Set.of("metal"), ProductionReachability.consumers(processes, "metal"));
    }

    private static ProductionChainAllocation allocation(long labour, long fuel) {
        var field = new SupplyPlanner.Process("field", "grain", 2, Map.of(), 0, Map.of(), 0, 10, 1, 20, 2, Map.of("person", labour));
        var oven = new SupplyPlanner.Process("oven", "food", 1, Map.of("grain", 2), 1, Map.of("fuel", 10L), 0,
                5, 1, 20, 3, Map.of("person", labour));
        return new ProductionChainAllocation(List.of(field, oven), Map.of("grain", 0L, "food", 0L, "fuel", fuel),
                Map.of(), Map.of("person", labour), Map.of(), Map.of("grain", 1L, "food", 5L, "fuel", 1L));
    }
    @Test void finalProductPullsItsRealUpstreamChainWithoutBuyingInputsOrDuplicatingLabour() {
        var original = allocation(25, 1);
        var selected = original.allocate("food", 10, 100, Set.of("oven"));
        assertEquals(5, selected.quantity()); assertEquals(0, selected.existingStock());
        assertEquals(Map.of("grain", 10L, "food", 5L), selected.next().additionalProduction());
        assertEquals(Map.of(), original.additionalProduction(), "advice never mutates its predecessor");
        assertEquals(0, selected.next().allocate("grain", 1, 100, Set.of("field")).quantity(), "same resident's labour is not reused");
    }
    @Test void missingFuelOrLateHarvestCannotCreateOutputAndFailureConsumesNothing() {
        assertEquals(0, allocation(25, 0).allocate("food", 10, 100, Set.of("oven")).quantity());
        var original = allocation(25, 1);
        var blocked = original.allocate("food", 5, 14, Set.of("oven"));
        assertEquals(0, blocked.quantity()); assertSame(original, blocked.next());
        assertEquals(5, blocked.next().allocate("food", 5, 100, Set.of("oven")).quantity());
        var futureSurplus = original.allocate("grain", 1, 100, Set.of("field")).next();
        assertEquals(0, futureSurplus.allocate("grain", 1, 9, Set.of("field")).quantity(),
                "the spare unit of a future two-unit batch is not stock available before harvest");
        assertEquals(1, futureSurplus.allocate("grain", 1, 100, Set.of("field")).quantity());
    }
}
