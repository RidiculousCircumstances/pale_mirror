package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoodsDispatchAdviceTest {
    @Test void selectedRecurringProductionPropagatesContinuityButAnInstalledAlternativeDoesNot() {
        assertEquals(TripDispatchPolicy.Continuity.RECURRING, GoodsDispatchAdvice.continuity(plan(true, true), "output"));
        assertEquals(TripDispatchPolicy.Continuity.RECURRING, GoodsDispatchAdvice.continuity(plan(true, true), "input"));
        assertEquals(TripDispatchPolicy.Continuity.FINITE, GoodsDispatchAdvice.continuity(plan(true, false), "input"),
                "the declared dependency graph alone cannot assign the needs of an unselected recipe");
        assertEquals(TripDispatchPolicy.Continuity.FINITE, GoodsDispatchAdvice.continuity(plan(false, true), "input"),
                "a finite output reserve does not invent continuous input consumption");
    }
    private static SettlementEconomicPlan plan(boolean recurring, boolean manufacture) {
        var demands = Map.of("input", new SupplyPlanner.Demand(1, 0, 0, 1, 100000,
                        Set.of(SupplyPlanner.Reason.PRODUCTION_INPUT), Optional.empty(), 0),
                "output", new SupplyPlanner.Demand(manufacture ? 0 : 10, manufacture ? 10 : 0, 0, 0, 100000,
                        Set.of(SupplyPlanner.Reason.STOCK_REPLENISHMENT), manufacture ? Optional.of("bench") : Optional.empty(), 0));
        var supply = new SupplyPlanner.Plan(demands, Map.of(), Map.of(), Optional.empty());
        return new SettlementEconomicPlan(1, 1, 0, 400, supply, supply, Map.of("input", 0L, "output", 0L),
                Map.of(), Map.of("input", Set.of("output"), "output", Set.of("output")), Optional.empty(),
                Map.of("input", new SupplyReservePlanner.Need(SupplyReservePlanner.Rate.ZERO, 0),
                        "output", new SupplyReservePlanner.Need(recurring ? SupplyReservePlanner.Rate.of(1, 100) : SupplyReservePlanner.Rate.ZERO, 10)),
                List.of());
    }
}
