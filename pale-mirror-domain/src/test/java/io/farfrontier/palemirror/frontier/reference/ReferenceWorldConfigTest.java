package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReferenceWorldConfigTest {
    @Test
    void sourceDefaultsMatchPinnedPythonWorldConfig() {
        ReferenceWorldConfig config = ReferenceWorldConfig.sourceV2();

        assertEquals(64, config.width());
        assertEquals(44, config.height());
        assertEquals(12, config.settlementCount());
        assertEquals(42L, config.seed());
        assertEquals(2, config.infectionSeeds());
        assertEquals(ReferenceSimulationProfile.SOURCE_V2, config.profile());
    }

    @Test
    void grayboxChangesOnlyItsExplicitProfileAndSeed() {
        ReferenceWorldConfig config = ReferenceWorldConfig.graybox1To40(7L);

        assertEquals(7L, config.seed());
        assertEquals(ReferenceSimulationProfile.GRAYBOX_1_40, config.profile());
        assertEquals(64, config.width());
        assertEquals(44, config.height());
        assertEquals(12, config.settlementCount());
    }

    @Test
    void rejectsInvalidWorldShape() {
        assertThrows(IllegalArgumentException.class, () -> new ReferenceWorldConfig(2, 44, 12, 42L,
                2, true, ReferenceSimulationProfile.SOURCE_V2));
        assertThrows(IllegalArgumentException.class, () -> new ReferenceWorldConfig(64, 44, 0, 42L,
                2, true, ReferenceSimulationProfile.SOURCE_V2));
    }
}
