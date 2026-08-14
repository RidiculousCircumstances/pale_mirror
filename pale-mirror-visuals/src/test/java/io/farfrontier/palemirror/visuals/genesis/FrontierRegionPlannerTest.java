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
        assertEquals(20, seed.modules().size());
        assertEquals(5, seed.expansionPlots().size());
        assertEquals(11, seed.definitionVersion());
        assertEquals(9, seed.primaryMineSite().initialModules().size());
        assertEquals(4, seed.alternateMineSite().initialModules().size());
        assertEquals(6, seed.primaryMineSite().foundations().size());
        assertEquals(6, seed.alternateMineSite().foundations().size());
        assertEquals(java.util.List.of("foundation", "shell", "machinery", "commissioning"),
                seed.alternateMineSite().stagedModules().stream().map(value -> value.stage()).toList());
        assertTrue(seed.primaryMineSite().semanticVolumes().stream()
                .anyMatch(value -> value.purpose().equals("INFECTION")));
        assertTrue(seed.primaryMineSite().bounds().contains(seed.primaryMineSite().controllerAnchor()));
        var surface = seed.primaryMineSite().surfaceBuildings();
        assertEquals(6, surface.size());
        assertTrue(surface.stream().flatMap(value -> value.modules().stream())
                .anyMatch(value -> value.templateId().endsWith("/mine/portal_hoist")));
        assertTrue(surface.stream().flatMap(value -> value.modules().stream())
                .anyMatch(value -> value.templateId().endsWith("/mine/loading_yard")));
        assertTrue(surface.stream().flatMap(value -> value.functions().stream())
                .anyMatch(value -> value.value().equals("pale_mirror:ore_processing")));
        assertTrue(seed.alternateMineSite().stagedModules().stream()
                .allMatch(value -> value.module().templateId().contains("/mine/dispatch_")));
        assertEquals(48, seed.residents().stream().map(value -> value.homeBuildingId() + ":" + value.homeSlotId())
                .distinct().count());
        var assigned = seed.residents().stream().filter(value -> !value.workplaceBuildingId().isBlank()).toList();
        assertEquals(24, assigned.size());
        assertEquals(assigned.size(), assigned.stream()
                .map(value -> value.workplaceBuildingId() + ":" + value.workplaceSlotId()).distinct().count());
        assertTrue(seed.residents().stream().allMatch(value -> value.role().startsWith("pale_mirror:")));
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
        assertTrue(horizontalDistanceSquared(seed.primaryMine(), seed.alternateMine()) >= 128L * 128L);
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

    @Test void surfaceBuildingsConsumeTheirOwnVerifiedFoundationLevels() {
        AuthoredRegionSeed seed = planner.plan(1123L, 0, new VisualPoint(0, 70, 0),
                FrontierClimate.TEMPERATE, (requirement, candidates) -> {
                    var role = requirement.role().equals(RegionPlacementProfiles.PRIMARY_MINE)
                            ? io.farfrontier.palemirror.api.AuthoredMineRole.PRIMARY
                            : io.farfrontier.palemirror.api.AuthoredMineRole.ALTERNATE;
                    java.util.Map<String, VisualPoint> centers = new java.util.LinkedHashMap<>();
                    int index = 0;
                    VisualPoint candidate = candidates.getFirst();
                    VisualPoint portal = new VisualPoint(candidate.x(), 72, candidate.z());
                    for (var pad : MineSurfaceLayout.pads(role)) {
                        centers.put(pad.id(), pad.center(portal, 0, 72 + index++ % 3));
                    }
                    return new MountainMineAnchor(portal, 0, centers);
                }, FrontierRegionPlanner::gradedManhattanRail);

        assertTrue(seed.primaryMineSite().foundations().stream().map(value -> value.targetY()).distinct().count() > 1);
        for (var foundation : seed.primaryMineSite().foundations()) {
            assertEquals(foundation.targetY(), foundation.footprint().min().y());
            assertEquals(foundation.targetY(), foundation.footprint().max().y());
        }
    }

    @Test void mineYardAndUndergroundWorkingsStayCompact() {
        AuthoredRegionSeed seed = planner.plan(621L, 0, new VisualPoint(0, 72, 0), FrontierClimate.TEMPERATE);
        var mine = seed.primaryMineSite();

        assertTrue(manhattan(mine.portal(), mine.loadingEndpoint()) <= 84);
        assertTrue(manhattan(mine.portal(), mine.controllerAnchor()) <= 60);
        for (var foundation : mine.foundations()) {
            VisualPoint center = new VisualPoint(
                    (foundation.footprint().min().x() + foundation.footprint().max().x()) / 2,
                    foundation.targetY(),
                    (foundation.footprint().min().z() + foundation.footprint().max().z()) / 2);
            assertTrue(horizontalDistanceSquared(mine.portal(), center) <= 80 * 80,
                    () -> foundation.id() + " escaped compact MineSite yard at " + center);
        }
    }

    @Test void exactMineBudgetSamplesLateralMountainFrontBeforeMoreRadialDistances() {
        VisualPoint settlement = new VisualPoint(0, 72, 0);
        SitePlacementRequirement primary = RegionPlacementProfiles.IRON_FRONTIER
                .requireSite(RegionPlacementProfiles.PRIMARY_MINE);
        var firstBudget = FrontierRegionPlanner.siteCandidates(settlement, 0, 240, primary, -1)
                .stream().limit(primary.exactValidationBudget()).toList();

        assertTrue(firstBudget.stream().anyMatch(point -> point.x() != 0 && point.z() != 0),
                "the exact budget must reach lateral offsets instead of spending every probe on cardinal axes");
        assertTrue(firstBudget.stream().map(point -> Math.abs(point.x()) + Math.abs(point.z()))
                .distinct().count() >= 3);
        assertTrue(primary.lateralOffsets().containsAll(java.util.List.of(-32, 32, -96, 96)),
                "mine-site search must resolve slopes between the former coarse 64-block rays");
    }

    @Test void mineSurfacePadsDoNotOverlapInAnyOrientation() {
        VisualPoint portal = new VisualPoint(0, 72, 0);
        for (var role : io.farfrontier.palemirror.api.AuthoredMineRole.values()) {
            for (int direction = 0; direction < 4; direction++) {
                var pads = MineSurfaceLayout.pads(role);
                for (int first = 0; first < pads.size(); first++) for (int second = first + 1;
                        second < pads.size(); second++) {
                    var firstBounds = pads.get(first).bounds(
                            MineSurfaceLayout.center(pads.get(first), portal, direction,
                                    new MineSurfaceLayout.YardOffset(0, 0)), direction);
                    var secondBounds = pads.get(second).bounds(
                            MineSurfaceLayout.center(pads.get(second), portal, direction,
                                    new MineSurfaceLayout.YardOffset(0, 0)), direction);
                    assertTrue(!overlaps(firstBounds, secondBounds), role + " direction " + direction
                            + " overlaps " + pads.get(first).id() + " and " + pads.get(second).id());
                }
            }
        }
    }

    private static int manhattan(VisualPoint a, VisualPoint b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
    }

    private static int horizontalDistanceSquared(VisualPoint a, VisualPoint b) {
        int dx = a.x() - b.x();
        int dz = a.z() - b.z();
        return dx * dx + dz * dz;
    }

    private static boolean overlaps(io.farfrontier.palemirror.api.VisualBounds first,
                                    io.farfrontier.palemirror.api.VisualBounds second) {
        return first.min().x() <= second.max().x() && first.max().x() >= second.min().x()
                && first.min().z() <= second.max().z() && first.max().z() >= second.min().z();
    }
}
