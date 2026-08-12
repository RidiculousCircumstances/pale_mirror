package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TerrainCandidateTest {
    @Test void choosesMedianAndAcceptsEightBlockRelief() {
        TerrainCandidate value = TerrainCandidate.evaluate(12, 18, List.of(
                new TerrainSample(0, 0, 63, false), new TerrainSample(1, 0, 67, false),
                new TerrainSample(2, 0, 71, false)));
        assertEquals(67, value.anchor().y());
        assertEquals(8, value.relief());
        assertTrue(value.preferred());
    }

    @Test void waterAlwaysLosesToDryFallback() {
        TerrainCandidate wet = TerrainCandidate.evaluate(0, 0, List.of(new TerrainSample(0, 0, 64, true)));
        TerrainCandidate dry = TerrainCandidate.evaluate(1, 0, List.of(new TerrainSample(1, 0, 70, false)));
        assertFalse(wet.preferred());
        assertEquals(dry, List.of(wet, dry).stream().min(TerrainCandidate.ordering()).orElseThrow());
    }
}
