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

    @Test void twentyRegionSurveyUsesHierarchicalProbeBudget() {
        FakeTerrain terrain = new FakeTerrain();
        new FrontierSiteSelector().select(42L, new VisualPoint(0, 0, 0), 20, 10_000, 1_400, terrain);
        int legacyDetailedProbeCount = 20 * 16 * 49;
        assertTrue(terrain.uniqueSamples() < 6_000,
                "hierarchical batch survey must remain far below twenty independent detailed searches");
        assertTrue(terrain.uniqueSamples() * 2 < legacyDetailedProbeCount,
                "batch survey must use less than half the legacy terrain probes");
    }

    @Test void impossibleMapFailsClosedInsteadOfOverlappingRegions() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                new FrontierSiteSelector().select(7L, new VisualPoint(0, 0, 0), 20, 3_000, 10_000,
                        new FakeTerrain()));
        assertTrue(failure.getMessage().contains("Cannot place 20 authored regions"));
    }

    private static long distanceSquared(VisualPoint first, VisualPoint second) {
        long dx = (long) first.x() - second.x();
        long dz = (long) first.z() - second.z();
        return dx * dx + dz * dz;
    }

    private static final class FakeTerrain implements FrontierSiteSelector.TerrainAccess {
        private final Map<Long, TerrainSample> samples = new HashMap<>();

        @Override public TerrainSample sample(int x, int z) {
            return samples.computeIfAbsent((long) x << 32 ^ Integer.toUnsignedLong(z), ignored ->
                    new TerrainSample(x, z, height(x, z), false));
        }

        private int height(int x, int z) {
            long value = (long) x * 31L + (long) z * 17L + ((long) x * z >>> 8);
            return 68 + Math.floorMod((int) value, 7);
        }

        @Override public FrontierClimate climate(int x, int z) {
            return FrontierClimate.values()[Math.floorMod((x >> 8) + (z >> 8), FrontierClimate.values().length)];
        }

        private int uniqueSamples() { return samples.size(); }
    }
}
