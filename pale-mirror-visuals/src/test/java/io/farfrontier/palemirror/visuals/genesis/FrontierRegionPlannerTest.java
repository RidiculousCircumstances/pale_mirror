package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FrontierRegionPlannerTest {
    private final FrontierRegionPlanner planner = new FrontierRegionPlanner();

    @Test void sameAddressProducesIdenticalManifest() {
        AuthoredRegionSeed first = planner.plan(42L, 0, new VisualPoint(1400, 72, -300), FrontierClimate.TEMPERATE);
        AuthoredRegionSeed second = planner.plan(42L, 0, new VisualPoint(1400, 72, -300), FrontierClimate.TEMPERATE);
        assertEquals(first, second);
        assertNotEquals(first, planner.plan(43L, 0, first.anchor(), FrontierClimate.TEMPERATE));
    }

    @Test void manifestRespectsPopulationAndSiteContract() {
        AuthoredRegionSeed seed = planner.plan(-19L, 2, new VisualPoint(-9000, 65, 9000), FrontierClimate.COLD_TAIGA);
        Map<String, Long> counts = seed.residents().stream().collect(Collectors.groupingBy(
                resident -> resident.cohort(), Collectors.counting()));
        assertEquals(Map.of("CIVILIANS", 20L, "WORKERS", 14L, "SPECIALISTS", 4L,
                "GUARDS", 6L, "CHILDREN", 4L), counts);
        assertEquals(48, seed.residents().stream().map(value -> value.residentId()).distinct().count());
        assertTrue(seed.modules().size() >= 16);
        assertEquals(6, seed.expansionPlots().size());
    }

    @Test void mineDistancesAndRailConnectivityAreBounded() {
        AuthoredRegionSeed seed = planner.plan(983L, 0, new VisualPoint(1500, 70, 0), FrontierClimate.DRY_ARID);
        int primary = manhattan(seed.anchor(), seed.primaryMine());
        int alternate = manhattan(seed.anchor(), seed.alternateMine());
        assertTrue(primary >= 384 && primary <= 512);
        assertTrue(alternate >= 512 && alternate <= 768);
        assertEquals(seed.freightGate(), seed.baselineRailNodes().getFirst());
        assertEquals(seed.primaryMine().x(), seed.baselineRailNodes().getLast().x());
        assertEquals(seed.primaryMine().z(), seed.baselineRailNodes().getLast().z());
        for (int i = 1; i < seed.baselineRailNodes().size(); i++) {
            assertEquals(1, manhattan(seed.baselineRailNodes().get(i - 1), seed.baselineRailNodes().get(i)));
        }
    }

    private static int manhattan(VisualPoint a, VisualPoint b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
    }
}
