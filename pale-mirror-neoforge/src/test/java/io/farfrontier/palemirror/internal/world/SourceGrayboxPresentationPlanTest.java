package io.farfrontier.palemirror.internal.world;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceGrayboxPresentationPlanTest {
    @Test
    void everySourceClaimRemainsSpatiallyDistinctThroughoutTheSeededFirstYear() {
        for (long seed : List.of(7L, 17L, 41L, 73L)) {
            ReferenceGrayboxSimulation simulation = ReferenceGrayboxSimulation.create(seed);
            for (int day = 0; day <= 365; day++) {
                int currentDay = day;
                assertDoesNotThrow(() -> SourceGrayboxPresentationPlan.validate(simulation.snapshot()),
                        "source projection must remain physically distinct for seed " + seed + " day " + currentDay);
                if (day < 365) simulation.tick();
            }
        }
    }
}
