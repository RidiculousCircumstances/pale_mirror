package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3NavigationPaceTest {
    @Test void sharedDistancePaceRespectsNativeAttributesWithoutChangingThem() {
        var pace = new TravelPace(1.0 / 20);
        for (double attribute : new double[]{0.3, 0.5}) {
            double modifier = FrontierV3NavigationPace.speedModifier(pace, attribute, 0.6, 0.7);
            double nativeSpeed = modifier * attribute;
            double steadyDistance = nativeSpeed * nativeSpeed * 0.98 * (0.21600002 / Math.pow(0.6, 3)) / (1 - 0.6 * 0.91);
            assertEquals(pace.blocksPerTick(), steadyDistance, 1e-10);
            assertTrue(modifier > 0 && modifier <= 0.7);
        }
        assertEquals(0.7, FrontierV3NavigationPace.speedModifier(pace, 0.01, 0.6, 0.7),
                "a slow body cannot exceed its native capability to satisfy a requested pace");
        assertEquals(0, FrontierV3NavigationPace.speedModifier(pace, 0, 0.6, 0.7));
        assertThrows(IllegalArgumentException.class, () -> new TravelPace(0));
        assertThrows(IllegalArgumentException.class, () -> new TravelPace(Double.NaN));
    }
}
