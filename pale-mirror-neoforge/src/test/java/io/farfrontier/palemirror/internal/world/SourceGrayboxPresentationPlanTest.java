package io.farfrontier.palemirror.internal.world;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceGrayboxPresentationPlanTest {
    @Test
    void sectorOverviewUsesMetricTowersAndBoundsFloatingLabels() {
        ReferenceGrayboxSimulation simulation = ReferenceGrayboxSimulation.create(9031746258841137206L);
        for (int day = 0; day < 40; day++) simulation.tick();
        var snapshot = simulation.snapshot();
        var desired = SourceGrayboxPresentationPlan.from(snapshot);

        assertTrue(desired.keySet().stream().anyMatch(id -> id.startsWith("sector-metric:") && id.endsWith(":infection")),
                "an infected sector must own a visible infection metric tower");
        assertTrue(desired.keySet().stream().anyMatch(id -> id.startsWith("sector-metric:") && id.endsWith(":human_access")),
                "human access must be physically distinct from infection and hive control");
        assertTrue(SourceGrayboxLabelLayout.labelledSectors(snapshot).size() <= 24,
                "a dense V2 map must never create an unbounded cloud of nameplates");
        assertEquals(SourceGrayboxLabelLayout.labelledSectors(snapshot), SourceGrayboxLabelLayout.labelledSectors(snapshot),
                "the bounded sector selection must remain deterministic for one immutable source snapshot");
    }

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
