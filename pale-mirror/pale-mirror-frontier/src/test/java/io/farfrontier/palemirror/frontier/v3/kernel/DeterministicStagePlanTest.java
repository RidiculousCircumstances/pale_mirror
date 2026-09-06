package io.farfrontier.palemirror.frontier.v3.kernel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeterministicStagePlanTest {
    @Test
    void topologicalPlanIsStableIndependentOfRegistrationOrder() {
        DeterministicStagePlan<String> plan = new DeterministicStagePlan<>(List.of(
                definition("scene", 2, Set.of("effect"), "scene"),
                definition("effect", 1, Set.of("observation"), "effect"),
                definition("observation", 0, Set.of(), "observation")));
        assertEquals(List.of("observation", "effect", "scene"), plan.ordered().stream().map(DeterministicStagePlan.Definition::id).toList());
    }

    @Test
    void duplicateWriterAndCycleFailBeforeExecution() {
        assertThrows(IllegalArgumentException.class, () -> new DeterministicStagePlan<>(List.of(
                definition("one", 1, Set.of(), "same"), definition("two", 1, Set.of(), "same"))));
        assertThrows(IllegalArgumentException.class, () -> new DeterministicStagePlan<>(List.of(
                definition("one", 1, Set.of("two"), "one"), definition("two", 1, Set.of("one"), "two"))));
    }

    @Test
    void reversingObservationBehindEffectIsRejectedBeforeExecution() {
        assertThrows(IllegalArgumentException.class, () -> new DeterministicStagePlan<>(List.of(
                definition("effect", 1, Set.of(), "effect"), definition("observation", 0, Set.of("effect"), "observation"))));
    }

    private static DeterministicStagePlan.Definition<String> definition(String id, int stage, Set<String> dependencies, String write) {
        return new DeterministicStagePlan.Definition<>(id, stage, dependencies, Set.of(write), id);
    }
}
