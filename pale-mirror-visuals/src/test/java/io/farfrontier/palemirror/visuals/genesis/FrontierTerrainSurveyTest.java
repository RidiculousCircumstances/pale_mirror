package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FrontierTerrainSurveyTest {
    @Test void batchSelectionIsDeterministicBoundedAndSeparated() {
        FakeTerrain firstTerrain = new FakeTerrain();
        FakeTerrain secondTerrain = new FakeTerrain();
        FrontierSiteSelector selector = new FrontierSiteSelector();
        List<FrontierSiteSelector.SelectedSite> first = selector.select(
                991_771L, new VisualPoint(120, 70, -80), 20, 10_000, 1_400, firstTerrain);
        List<FrontierSiteSelector.SelectedSite> second = selector.select(
                991_771L, new VisualPoint(120, 70, -80), 20, 10_000, 1_400, secondTerrain);

        assertEquals(first, second);
        assertEquals(20, first.size());
        for (int index = 0; index < first.size(); index++) {
            VisualPoint point = first.get(index).terrain().anchor();
            assertTrue(distanceSquared(point, new VisualPoint(120, 70, -80)) <= 10_000L * 10_000L);
            for (int other = 0; other < index; other++) assertTrue(
                    distanceSquared(point, first.get(other).terrain().anchor()) >= 1_400L * 1_400L);
        }
    }

    @Test void twentyRegionSurveyHasHardExactHeightBudget() {
        FakeTerrain terrain = new FakeTerrain();
        List<FrontierSiteSelector.SelectedSite> sites = new FrontierSiteSelector().select(
                42L, new VisualPoint(0, 0, 0), 20, 10_000, 1_400, terrain);
        java.util.concurrent.atomic.AtomicInteger mineSamples = new java.util.concurrent.atomic.AtomicInteger();
        FrontierRegionPlanner planner = new FrontierRegionPlanner();
        for (int ordinal = 0; ordinal < sites.size(); ordinal++) {
            FrontierSiteSelector.SelectedSite site = sites.get(ordinal);
            planner.plan(42L, ordinal, site.terrain().anchor(), site.climate(), (x, z) -> {
                mineSamples.incrementAndGet();
                return terrain.height(x, z);
            });
        }
        int hardBudget = 20 * (FrontierSiteSelector.MAX_EXACT_CANDIDATES_PER_REGION
                * FrontierSiteSelector.EXACT_SAMPLES_PER_CANDIDATE + 2);
        assertTrue(terrain.exactSamples + mineSamples.get() <= hardBudget);
        assertEquals(20 * FrontierSiteSelector.EXACT_SAMPLES_PER_CANDIDATE, terrain.exactSamples,
                "flat valid terrain should consume one center/cardinal survey per region");
        assertEquals(40, mineSamples.get(), "each region may query only its two mine anchors");
    }

    @Test void rejectedSitesConsumeBudgetAndFailClosed() {
        FakeTerrain terrain = new FakeTerrain();
        terrain.water = true;
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                new FrontierSiteSelector().select(7L, new VisualPoint(0, 0, 0), 1, 10_000, 1_400, terrain));
        assertTrue(failure.getMessage().contains("exact survey budget"));
        assertEquals(FrontierSiteSelector.MAX_EXACT_CANDIDATES_PER_REGION
                * FrontierSiteSelector.EXACT_SAMPLES_PER_CANDIDATE, terrain.exactSamples);
    }

    @Test void unsuitableBiomesNeverSpendExactHeightBudget() {
        FakeTerrain terrain = new FakeTerrain();
        terrain.suitable = false;
        assertThrows(IllegalStateException.class, () -> new FrontierSiteSelector().select(
                9L, new VisualPoint(0, 0, 0), 1, 10_000, 1_400, terrain));
        assertEquals(0, terrain.exactSamples);
    }

    @Test void impossibleMapFailsClosedInsteadOfOverlappingRegions() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                new FrontierSiteSelector().select(7L, new VisualPoint(0, 0, 0), 20, 3_000, 10_000,
                        new FakeTerrain()));
        assertTrue(failure.getMessage().contains("Cannot place 20 authored regions"));
    }

    @Test void missingOptionalReserveDoesNotRejectRequestedSites() {
        List<FrontierSiteSelector.SelectedSite> sites = new FrontierSiteSelector().selectWithReserve(
                7L, new VisualPoint(0, 0, 0), 1, 1, 3_000, 10_000, new FakeTerrain());

        assertEquals(1, sites.size());
    }

    @Test void mineFootprintRejectsWaterAtTheLoadingYardEdge() {
        VisualPoint mine = new VisualPoint(100, 70, 200);
        FakeTerrain terrain = new FakeTerrain();
        terrain.waterAt = new VisualPoint(mine.x(), 0, mine.z() + FrontierTerrainSurvey.MINE_MAX_Z);
        assertTrue(!FrontierTerrainSurvey.dryMineFootprint(mine, terrain));
    }

    @Test void exactMineSurfaceRejectsWaterColumnAndAcceptsDryGround() {
        assertTrue(FrontierTerrainSurvey.isDrySurface(72, 72));
        assertTrue(!FrontierTerrainSurvey.isDrySurface(63, 41));
    }

    private static long distanceSquared(VisualPoint first, VisualPoint second) {
        long dx = (long) first.x() - second.x();
        long dz = (long) first.z() - second.z();
        return dx * dx + dz * dz;
    }

    private static final class FakeTerrain implements FrontierSiteSelector.TerrainAccess {
        private final Map<Long, TerrainSample> samples = new HashMap<>();
        private int exactSamples;
        private boolean suitable = true;
        private boolean water;
        private VisualPoint waterAt;

        @Override public TerrainSample exactSample(int x, int z) {
            exactSamples++;
            return samples.computeIfAbsent((long) x << 32 ^ Integer.toUnsignedLong(z), ignored ->
                    new TerrainSample(x, z, height(x, z), water));
        }

        @Override public FrontierSiteSelector.BiomeSample biome(int x, int z) {
            FrontierClimate climate = FrontierClimate.values()[Math.floorMod(
                    (x >> 8) + (z >> 8), FrontierClimate.values().length)];
            boolean localWater = waterAt != null && waterAt.x() == x && waterAt.z() == z;
            return new FrontierSiteSelector.BiomeSample(climate, suitable && !localWater, localWater || !suitable, 0);
        }

        private int height(int x, int z) {
            long value = (long) x * 31L + (long) z * 17L + ((long) x * z >>> 8);
            return 68 + Math.floorMod((int) value, 7);
        }
    }
}
