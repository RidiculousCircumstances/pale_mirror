package io.farfrontier.palemirror.frontier.v3.model.supply;

import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SupplyReservePlannerTest {
    private static final ReservePolicy POLICY = new ReservePolicy(20, 500);
    private static final Need NONE = new Need(Rate.ZERO, 0);
    private static Map<String, Timing> timings(Set<String> items, long lead) {
        var result = new TreeMap<String, Timing>();
        items.forEach(i -> result.put(i, new Timing(lead, TimingBasis.LOCAL_ESTIMATE))); return result;
    }
    @Test void stockTargetsFollowConsumptionLeadAndSafetyWithoutSpeculativeMining() {
        var needs = Map.of("food", new Need(Rate.of(2, 100), 3), "ore", NONE);
        var planner = new SupplyReservePlanner();
        var targets = planner.plan(needs, List.of(), timings(needs.keySet(), 200), POLICY);
        assertEquals(10, targets.get("food").quantity());
        assertEquals(9, targets.get("food").reorderPoint()); assertEquals(5, targets.get("food").protectedQuantity());
        assertEquals(0, targets.get("ore").quantity());
        assertTrue(planner.plan(needs, List.of(), timings(needs.keySet(), 400), POLICY).get("food").quantity() > 10);
        assertTrue(planner.plan(Map.of("food", new Need(Rate.of(4, 100), 3)), List.of(), timings(Set.of("food"), 200), POLICY)
                .get("food").quantity() > 10);
    }
    @Test void sharedMultiStageInputsAggregateOnceWithoutRoundingEachConsumersRate() {
        var needs = Map.of("food", new Need(Rate.of(1, 100), 0), "medicine", new Need(Rate.of(1, 100), 0), "fuel", NONE, "ore", NONE);
        var flows = List.of(new Flow("food", Map.of("fuel", Rate.of(1, 20))),
                new Flow("medicine", Map.of("fuel", Rate.of(1, 10))), new Flow("fuel", Map.of("ore", Rate.of(3, 1))));
        var result = new SupplyReservePlanner().plan(needs, flows, timings(needs.keySet(), 1000), POLICY);
        assertEquals(Rate.of(3, 2000), result.get("fuel").consumption());
        assertEquals(Rate.of(9, 2000), result.get("ore").consumption());
        assertEquals(3, result.get("fuel").quantity()); assertEquals(7, result.get("ore").quantity());
    }
    @Test void incomingCoversAdviceAndExplicitReorderPreservesHysteresis() {
        var policy = new SupplyPolicy(500, 1, 64, 100, 500, false);
        var stocked = new SupplyPlanner.Stock(91, 0, 0, 100, 20, 0, 1, policy, List.of(), OptionalLong.of(90));
        var arriving = new SupplyPlanner.Stock(0, 100, 0, 100, 20, 0, 1, policy, List.of(), OptionalLong.of(90));
        assertEquals(0, new SupplyPlanner().plan(Map.of("fuel", stocked), List.of()).require("fuel").purchase());
        assertEquals(0, new SupplyPlanner().plan(Map.of("fuel", arriving), List.of()).require("fuel").purchase());
    }
    @Test void invalidDeclarationsCannotSilentlyChangeEconomicMeaning() {
        var needs = Map.of("a", NONE, "b", NONE); var timing = timings(needs.keySet(), 100);
        var planner = new SupplyReservePlanner(); var a = new Flow("a", Map.of("b", Rate.of(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(needs, List.of(a, a), timing, POLICY));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(needs, List.of(a, new Flow("b", Map.of("a", Rate.of(1, 1)))), timing, POLICY));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(needs, List.of(new Flow("a", Map.of("unknown", Rate.of(1, 1)))), timing, POLICY));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(needs, List.of(), Map.of(), POLICY));
    }
}
