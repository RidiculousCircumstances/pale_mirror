package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TerrainCandidateTest {
    @Test void choosesMedianAndAcceptsEightBlockRelief() {
        TerrainCandidate value = TerrainCandidate.evaluate(12, 18, List.of(
                new TerrainSample(-60, 18, 63, false), new TerrainSample(12, 18, 67, false),
                new TerrainSample(84, 18, 71, false)));
        assertEquals(67, value.anchor().y());
        assertEquals(8, value.relief());
        SettlementTerrainPolicy policy = RegionPlacementProfiles.IRON_FRONTIER.settlementTerrain();
        assertTrue(policy.preferred(value));
    }

    @Test void waterAlwaysLosesToDryFallback() {
        TerrainCandidate wet = TerrainCandidate.evaluate(0, 0, List.of(new TerrainSample(0, 0, 64, true)));
        TerrainCandidate dry = TerrainCandidate.evaluate(1, 0, List.of(new TerrainSample(1, 0, 70, false)));
        SettlementTerrainPolicy policy = RegionPlacementProfiles.IRON_FRONTIER.settlementTerrain();
        assertFalse(policy.preferred(wet));
        assertEquals(dry, List.of(wet, dry).stream().min(TerrainCandidate.ordering(policy)).orElseThrow());
    }

    @Test void smoothBroadSlopeRemainsBuildable() {
        List<TerrainSample> samples = grid((x, z) -> 70 + Math.floorDiv(x + 72, 24));

        TerrainCandidate candidate = TerrainCandidate.evaluate(0, 0, samples, 2);

        assertTrue(RegionPlacementProfiles.IRON_FRONTIER.settlementTerrain().acceptable(candidate));
        assertEquals(0, candidate.surfaceQuality().maximumRoughness());
        assertEquals(100, candidate.surfaceQuality().buildablePercent());
    }

    @Test void localCanyonIsRejectedEvenWhenOuterReliefLooksBuildable() {
        List<TerrainSample> samples = grid((x, z) -> Math.abs(x) <= 24 && Math.abs(z) <= 24 ? 61 : 70);

        TerrainCandidate candidate = TerrainCandidate.evaluate(0, 0, samples, 2);

        assertFalse(RegionPlacementProfiles.IRON_FRONTIER.settlementTerrain().acceptable(candidate));
        assertTrue(candidate.surfaceQuality().depressionDepth() >= 5);
        assertTrue(candidate.surfaceQuality().maximumLocalGrade() > 4);
    }

    @Test void denseSnapshotCatchesAChannelBetweenCoarseSelectorPoints() {
        VisualPoint anchor = new VisualPoint(0, 70, 0);
        Map<Long, Integer> heights = new LinkedHashMap<>();
        for (int x = -72; x <= 72; x += 16) for (int z = -96; z <= 96; z += 16) {
            int height = x == -8 ? 62 : 70;
            heights.put(SettlementTerrainSnapshot.key(x, z), height);
        }
        SettlementTerrainSnapshot snapshot = new SettlementTerrainSnapshot(anchor, heights,
                (x, z) -> heights.getOrDefault(SettlementTerrainSnapshot.key(x, z), 70), (x, z) -> false);

        assertThrows(DryMineSiteUnavailableException.class, () -> snapshot.requireSuitable(
                RegionPlacementProfiles.IRON_FRONTIER.settlementTerrain()));
    }

    private static List<TerrainSample> grid(java.util.function.IntBinaryOperator height) {
        List<TerrainSample> samples = new ArrayList<>();
        for (int x = -72; x <= 72; x += 24) for (int z = -72; z <= 72; z += 24) {
            samples.add(new TerrainSample(x, z, height.applyAsInt(x, z), false));
        }
        return List.copyOf(samples);
    }
}
