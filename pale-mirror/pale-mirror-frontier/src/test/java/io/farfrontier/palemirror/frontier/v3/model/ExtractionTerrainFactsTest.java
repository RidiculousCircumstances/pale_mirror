package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockExclusions;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionTerrainFactsTest {
    private static FrontierBootstrap bootstrap(String rules) {
        return FrontierBootstrapper.create(new WorldId("frontier:terrain-facts"), 20260918065L, FrontierRulesets.installed(rules));
    }

    @Test void coverageAndRetainedSamplesAgreeAcrossStratumCapAndUnknownWorldBounds() {
        var bootstrap = bootstrap("frontier-v3-quarry-graybox-r7");
        for (int y : new int[]{-64, -63, 62, 63, 64, 2047}) {
            var position = new BlockPosition(0, y, 0);
            assertEquals(ExtractionTerrainFacts.at(bootstrap, position).isPresent(), ExtractionTerrainFacts.contains(bootstrap, position));
        }
        var stone = new BlockPosition(0, 62, 0);
        assertSame(ExtractionTerrainFacts.at(bootstrap, stone).orElseThrow(),
                ExtractionTerrainFacts.at(bootstrap, new BlockPosition(1, 61, 1)).orElseThrow(),
                "unchanged terrain queries reuse the validated immutable sample, not per-voxel regex/block construction");
        var outside = new BlockPosition(10000, 62, 10000);
        assertFalse(ExtractionTerrainFacts.contains(bootstrap, outside));
        assertTrue(ExtractionTerrainFacts.at(bootstrap, outside).isEmpty());
    }

    @Test void immutableCoverageProofCannotAuthorizeChangedExclusionsOrDifferentBootstrap() {
        var known = bootstrap("frontier-v3-quarry-graybox-r7");
        var exclusions = new KnownBlockExclusions(Set.of(new BlockPosition(0, 62, 0)));
        assertDoesNotThrow(() -> ExtractionTerrainFacts.validateExclusions(known, exclusions));
        assertDoesNotThrow(() -> ExtractionTerrainFacts.validateExclusions(known, exclusions));
        var invalid = exclusions.invalidate(new BlockPosition(10000, 62, 10000));
        assertThrows(IllegalArgumentException.class, () -> ExtractionTerrainFacts.validateExclusions(known, invalid));
        var withoutGeology = bootstrap("frontier-v3-quarry-graybox-r1");
        assertThrows(IllegalArgumentException.class, () -> ExtractionTerrainFacts.validateExclusions(withoutGeology, exclusions));
        assertTrue(ExtractionTerrainFacts.at(withoutGeology, new BlockPosition(0, 62, 0)).isEmpty());
        assertEquals("minecraft:stone", ExtractionTerrainFacts.at(known, new BlockPosition(0, 62, 0)).orElseThrow().block().kind());
    }
}
