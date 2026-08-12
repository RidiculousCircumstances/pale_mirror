package io.farfrontier.palemirror.internal.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AmbientSpawnThrottleTest {
    private static final AmbientSpawnThrottle.Policy POLICY =
            new AmbientSpawnThrottle.Policy(0.20D, 0.55D, 24, 32);

    @Test
    void hostileBackgroundIsThrottledMoreStronglyThanPeacefulBackground() {
        var empty = new AmbientSpawnThrottle.PopulationCounts(0, 0);

        assertTrue(AmbientSpawnThrottle.denies(
                AmbientSpawnThrottle.Population.HOSTILE, true, true, empty, 1, 0.30D, POLICY));
        assertFalse(AmbientSpawnThrottle.denies(
                AmbientSpawnThrottle.Population.PEACEFUL, true, true, empty, 1, 0.30D, POLICY));
    }

    @Test
    void capsScaleWithActivePlayers() {
        var counts = new AmbientSpawnThrottle.PopulationCounts(48, 63);

        assertTrue(AmbientSpawnThrottle.denies(
                AmbientSpawnThrottle.Population.HOSTILE, true, true, counts, 2, 0.0D, POLICY));
        assertFalse(AmbientSpawnThrottle.denies(
                AmbientSpawnThrottle.Population.PEACEFUL, true, true, counts, 2, 0.0D, POLICY));
    }

    @Test
    void explicitMechanicsAndPmMaterializationAreNeverThrottled() {
        var saturated = new AmbientSpawnThrottle.PopulationCounts(999, 999);

        assertFalse(AmbientSpawnThrottle.denies(
                AmbientSpawnThrottle.Population.HOSTILE, false, true, saturated, 1, 0.99D, POLICY));
        assertFalse(AmbientSpawnThrottle.denies(
                AmbientSpawnThrottle.Population.PEACEFUL, false, true, saturated, 1, 0.99D, POLICY));
    }

    @Test
    void unmanagedCategoriesAndVanillaFailuresRemainUntouched() {
        var saturated = new AmbientSpawnThrottle.PopulationCounts(999, 999);

        assertFalse(AmbientSpawnThrottle.denies(
                AmbientSpawnThrottle.Population.UNMANAGED, true, true, saturated, 1, 0.99D, POLICY));
        assertFalse(AmbientSpawnThrottle.denies(
                AmbientSpawnThrottle.Population.HOSTILE, true, false, saturated, 1, 0.99D, POLICY));
    }
}
