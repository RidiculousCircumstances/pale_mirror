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
        FrontierSiteSelector selector = new FrontierSiteSelector(RegionPlacementProfiles.IRON_FRONTIER);
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
        List<FrontierSiteSelector.SelectedSite> sites = new FrontierSiteSelector(RegionPlacementProfiles.IRON_FRONTIER).select(
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
        int exactCandidateBudget = RegionPlacementProfiles.IRON_FRONTIER.search()
                .exactSettlementCandidatesPerRegion(20);
        int samplesPerCandidate = FrontierSiteSelector.exactSamplesPerCandidate(
                RegionPlacementProfiles.IRON_FRONTIER.settlementTerrain());
        int hardBudget = 20 * (exactCandidateBudget
                * samplesPerCandidate + 2);
        assertTrue(terrain.exactSamples + mineSamples.get() <= hardBudget);
        assertEquals(20 * samplesPerCandidate, terrain.exactSamples,
                "flat valid terrain should consume one bounded suitability grid per region");
        assertEquals(40, mineSamples.get(), "each region may query only its two mine anchors");
    }

    @Test void rejectedSitesConsumeBudgetAndFailClosed() {
        FakeTerrain terrain = new FakeTerrain();
        terrain.water = true;
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                new FrontierSiteSelector(RegionPlacementProfiles.IRON_FRONTIER).select(
                        7L, new VisualPoint(0, 0, 0), 1, 10_000, 1_400, terrain));
        assertTrue(failure.getMessage().contains("exact survey budget"));
        int exactCandidateBudget = RegionPlacementProfiles.IRON_FRONTIER.search()
                .exactSettlementCandidatesPerRegion(1);
        int samplesPerCandidate = FrontierSiteSelector.exactSamplesPerCandidate(
                RegionPlacementProfiles.IRON_FRONTIER.settlementTerrain());
        assertEquals(exactCandidateBudget
                * samplesPerCandidate, terrain.exactSamples);
    }

    @Test void unsuitableBiomesNeverSpendExactHeightBudget() {
        FakeTerrain terrain = new FakeTerrain();
        terrain.suitable = false;
        assertThrows(IllegalStateException.class, () -> new FrontierSiteSelector(RegionPlacementProfiles.IRON_FRONTIER).select(
                9L, new VisualPoint(0, 0, 0), 1, 10_000, 1_400, terrain));
        assertEquals(0, terrain.exactSamples);
    }

    @Test void denseVegetationFootprintNeverSpendsExactHeightBudget() {
        FakeTerrain terrain = new FakeTerrain();
        terrain.vegetationBurden = 3;
        assertThrows(IllegalStateException.class, () -> new FrontierSiteSelector(
                RegionPlacementProfiles.IRON_FRONTIER).select(
                9L, new VisualPoint(0, 0, 0), 1, 10_000, 1_400, terrain));
        assertEquals(0, terrain.exactSamples);
    }

    @Test void impossibleMapFailsClosedInsteadOfOverlappingRegions() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                new FrontierSiteSelector(RegionPlacementProfiles.IRON_FRONTIER).select(
                        7L, new VisualPoint(0, 0, 0), 20, 3_000, 10_000,
                        new FakeTerrain()));
        assertTrue(failure.getMessage().contains("Cannot place 20 authored regions"));
    }

    @Test void missingOptionalReserveDoesNotRejectRequestedSites() {
        List<FrontierSiteSelector.SelectedSite> sites = new FrontierSiteSelector(
                RegionPlacementProfiles.IRON_FRONTIER).selectWithReserve(
                7L, new VisualPoint(0, 0, 0), 1, 1, 3_000, 10_000, new FakeTerrain());

        assertTrue(!sites.isEmpty());
    }

    @Test void reserveCentersDoNotSelectTheLargeWorldSearchBudget() {
        FakeTerrain terrain = new FakeTerrain();
        List<FrontierSiteSelector.SelectedSite> sites = new FrontierSiteSelector(
                RegionPlacementProfiles.IRON_FRONTIER).selectWithReserve(
                7L, new VisualPoint(0, 0, 0), 3, 48, 20_000, 2_500, terrain);

        assertEquals(51, sites.size());
        assertTrue(terrain.biomeSamples < 1_000_000,
                "optional feasibility reserves must not trigger a large-world landscape scan: "
                        + terrain.biomeSamples);
    }

    @Test void mineFootprintRejectsWaterAtTheLoadingYardEdge() {
        VisualPoint mine = new VisualPoint(100, 70, 200);
        FakeTerrain terrain = new FakeTerrain();
        SiteTerrainPolicy minePolicy = RegionPlacementProfiles.IRON_FRONTIER
                .requireSite(RegionPlacementProfiles.PRIMARY_MINE).terrain();
        terrain.waterAt = new VisualPoint(mine.x(), 0, mine.z() + minePolicy.footprintHalfExtent());
        assertTrue(!FrontierTerrainSurvey.coarseDryFootprint(mine, minePolicy, terrain));
    }

    @Test void exactMineFootprintRejectsInlandWaterInsideDryBiome() {
        VisualPoint mine = new VisualPoint(100, 70, 200);
        FakeTerrain terrain = new FakeTerrain();
        SiteTerrainPolicy minePolicy = RegionPlacementProfiles.IRON_FRONTIER
                .requireSite(RegionPlacementProfiles.ALTERNATE_MINE).terrain();
        terrain.actualWaterAt = new VisualPoint(mine.x() + minePolicy.footprintHalfExtent(), 0, mine.z());

        assertTrue(FrontierTerrainSurvey.coarseDryFootprint(mine, minePolicy, terrain),
                "a biome-only prefilter cannot identify an inland lake");
        assertTrue(!FrontierTerrainSurvey.exactDryFootprint(mine, minePolicy, terrain),
                "the exact hydrology pass must reject inland water for alternate mines too");
    }

    @Test void mineFootprintDoesNotTreatBiomeNamingAsAuthoritativeMountainEvidence() {
        VisualPoint mine = new VisualPoint(100, 70, 200);
        FakeTerrain terrain = new FakeTerrain();
        terrain.mountainNetwork = false;
        SiteTerrainPolicy minePolicy = RegionPlacementProfiles.IRON_FRONTIER
                .requireSite(RegionPlacementProfiles.PRIMARY_MINE).terrain();
        assertTrue(FrontierTerrainSurvey.coarseDryFootprint(mine, minePolicy, terrain),
                "a dry candidate must reach exact height profiling even without a mountain-named biome");
        terrain.mountainAt = new VisualPoint(mine.x() + 32, 0, mine.z());
        assertTrue(FrontierTerrainSurvey.coarseDryFootprint(mine, minePolicy, terrain));
    }

    private static long distanceSquared(VisualPoint first, VisualPoint second) {
        long dx = (long) first.x() - second.x();
        long dz = (long) first.z() - second.z();
        return dx * dx + dz * dz;
    }

    private static final class FakeTerrain implements FrontierSiteSelector.TerrainAccess {
        private final Map<Long, TerrainSample> samples = new HashMap<>();
        private int exactSamples;
        private int biomeSamples;
        private boolean suitable = true;
        private boolean water;
        private boolean mountainNetwork = true;
        private int vegetationBurden;
        private VisualPoint waterAt;
        private VisualPoint actualWaterAt;
        private VisualPoint mountainAt;

        @Override public TerrainSample exactSample(int x, int z) {
            exactSamples++;
            return samples.computeIfAbsent((long) x << 32 ^ Integer.toUnsignedLong(z), ignored ->
                    new TerrainSample(x, z, height(x, z), water));
        }

        @Override public FrontierSiteSelector.BiomeSample biome(int x, int z) {
            biomeSamples++;
            FrontierClimate climate = FrontierClimate.values()[Math.floorMod(
                    (x >> 8) + (z >> 8), FrontierClimate.values().length)];
            boolean localWater = waterAt != null && waterAt.x() == x && waterAt.z() == z;
            boolean mountain = mountainNetwork || mountainAt != null && mountainAt.x() == x && mountainAt.z() == z;
            return new FrontierSiteSelector.BiomeSample(climate, suitable && !localWater, localWater || !suitable,
                    0, mountain, vegetationBurden, vegetationBurden <= RegionPlacementProfiles.IRON_FRONTIER
                            .settlementTerrain().surfaceSuitability().maximumVegetationBurden());
        }

        @Override public boolean exactWater(int x, int z) {
            return water || actualWaterAt != null && actualWaterAt.x() == x && actualWaterAt.z() == z;
        }

        private int height(int x, int z) {
            return 68;
        }
    }
}
