package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FiniteWorkForecastTest {
    @Test void sequentialOutputsIncludeSharedOpeningWorkOnceNotJustTheExposedCell() {
        var result = FiniteWorkForecast.assess(List.of(
                node(1, Set.of(), 30, false), node(2, Set.of(1L), 10, true),
                node(3, Set.of(2L), 10, true), node(4, Set.of(1L), 10, true)));
        assertEquals(new FiniteWorkForecast.Estimate(3, 60, 40), result);
        assertEquals(new FiniteWorkForecast.Estimate(1, 60, 60), FiniteWorkForecast.assess(List.of(
                node(1, Set.of(), 30, false), node(2, Set.of(), 20, false), node(3, Set.of(1L, 2L), 10, true))),
                "two independent prerequisites still consume labour before the first output");
    }
    @Test void blockedPrerequisiteExcludesItsDescendantsAndReservedOutputIsNotPromisedAgain() {
        var result = FiniteWorkForecast.assess(List.of(
                new FiniteWorkForecast.Node(1, Set.of(), OptionalLong.empty(), false),
                node(2, Set.of(1L), 10, true), node(3, Set.of(), 10, false),
                node(4, Set.of(3L), 10, true)));
        assertEquals(new FiniteWorkForecast.Estimate(1, 20, 20), result);
    }
    @Test void missingDeclarationsFailRatherThanInventingACompletedDependency() {
        assertThrows(IllegalArgumentException.class, () -> FiniteWorkForecast.assess(List.of(node(2, Set.of(1L), 10, true))));
        assertThrows(IllegalArgumentException.class, () -> FiniteWorkForecast.assess(List.of(node(1, Set.of(), 10, true), node(1, Set.of(), 10, true))));
    }
    private static FiniteWorkForecast.Node node(long id, Set<Long> prerequisites, long ticks, boolean output) {
        return new FiniteWorkForecast.Node(id, prerequisites, OptionalLong.of(ticks), output);
    }
}
