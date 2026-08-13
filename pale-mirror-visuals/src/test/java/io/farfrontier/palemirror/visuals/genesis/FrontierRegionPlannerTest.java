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
        assertEquals(6, seed.definitionVersion());
        assertEquals(8, seed.primaryMineSite().initialModules().size());
        assertEquals(4, seed.alternateMineSite().initialModules().size());
        assertEquals(java.util.List.of("foundation", "shell", "machinery", "commissioning"),
                seed.alternateMineSite().stagedModules().stream().map(value -> value.stage()).toList());
        assertTrue(seed.primaryMineSite().semanticVolumes().stream()
                .anyMatch(value -> value.purpose().equals("INFECTION")));
        assertTrue(seed.primaryMineSite().bounds().contains(seed.primaryMineSite().controllerAnchor()));
    }

    @Test void railwayConsumesOnlyTheTwoExplicitMineAnchorQueries() {
        java.util.concurrent.atomic.AtomicInteger queries = new java.util.concurrent.atomic.AtomicInteger();
        AuthoredRegionSeed seed = planner.plan(983L, 0, new VisualPoint(1500, 70, 0),
                FrontierClimate.DRY_ARID, (x, z) -> {
                    queries.incrementAndGet();
                    return x == 1500 && z == 0 ? 70 : 82;
                });
        assertEquals(2, queries.get());
        assertEquals(seed.primaryMine().y() + 1, seed.baselineRailNodes().getLast().y());
    }

    @Test void mineDistancesAndRailConnectivityAreBounded() {
        AuthoredRegionSeed seed = planner.plan(983L, 0, new VisualPoint(1500, 70, 0), FrontierClimate.DRY_ARID);
        int primary = manhattan(seed.anchor(), seed.primaryMine());
        int alternate = manhattan(seed.anchor(), seed.alternateMine());
        assertTrue(primary >= 160 && primary <= 320);
        assertTrue(alternate >= 320 && alternate <= 560);
        assertEquals(seed.receivingDepot().x(), seed.baselineRailNodes().getFirst().x());
        assertEquals(seed.receivingDepot().z(), seed.baselineRailNodes().getFirst().z());
        assertEquals(seed.primaryMineSite().loadingEndpoint().x(), seed.baselineRailNodes().getLast().x());
        assertEquals(seed.primaryMineSite().loadingEndpoint().z(), seed.baselineRailNodes().getLast().z());
        for (int i = 1; i < seed.baselineRailNodes().size(); i++) {
            assertEquals(1, manhattan(seed.baselineRailNodes().get(i - 1), seed.baselineRailNodes().get(i)));
            assertTrue(Math.abs(seed.baselineRailNodes().get(i - 1).y() - seed.baselineRailNodes().get(i).y()) <= 1);
        }
    }

    @Test void dryResolverMayMovePrimaryMineAndFreightGateTogether() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        AuthoredRegionSeed seed = planner.plan(983L, 0, new VisualPoint(1500, 70, 0),
                FrontierClimate.DRY_ARID, (requirement, candidates) -> {
                    int call = calls.getAndIncrement();
                    VisualPoint selected = call == 0
                            ? candidates.stream().filter(point -> point.x() == 1500 && point.z() > 0)
                                    .findFirst().orElseThrow()
                            : candidates.getFirst();
                    VisualPoint portal = new VisualPoint(selected.x(), 76 + call, selected.z());
                    return new MountainMineAnchor(portal, call == 0 ? 3 : 0);
                }, FrontierRegionPlanner::gradedManhattanRail);

        assertEquals(2, calls.get());
        assertEquals(seed.anchor().x(), seed.primaryMine().x());
        assertTrue(seed.primaryMine().z() > seed.anchor().z());
        assertEquals(seed.anchor().x(), seed.freightGate().x());
        assertTrue(seed.freightGate().z() > seed.anchor().z());
        assertEquals(seed.primaryMineSite().loadingEndpoint().x(), seed.baselineRailNodes().getLast().x());
        assertEquals(seed.primaryMineSite().loadingEndpoint().z(), seed.baselineRailNodes().getLast().z());
    }

    private static int manhattan(VisualPoint a, VisualPoint b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
    }
}
