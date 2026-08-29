package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3LaunchOwnershipTest {
    private static final String PROPERTY = "pale_mirror.frontier_v3.enabled";

    @Test
    void enabledV3LaunchRetainsPhysicalWorldOwnershipEvenWithoutAnActiveRuntime() {
        String prior = System.getProperty(PROPERTY);
        try {
            System.clearProperty(PROPERTY);
            assertFalse(FrontierV3ServerLifecycle.v3LaunchOwnsPhysicalWorld(),
                    "a normal legacy launch may own its legacy graybox");

            System.setProperty(PROPERTY, "true");
            assertTrue(FrontierV3ServerLifecycle.v3LaunchOwnsPhysicalWorld(),
                    "a v3 launch must exclude v2 even before startup or after a v3 quarantine");
        } finally {
            if (prior == null) System.clearProperty(PROPERTY);
            else System.setProperty(PROPERTY, prior);
        }
    }
}
