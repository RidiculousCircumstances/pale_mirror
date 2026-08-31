package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3LaunchOwnershipTest {
    private static final String PROPERTY = "pale_mirror.frontier_v3.enabled";
    private static final String FIXTURE_PROFILE_PROPERTY = "pale_mirror.frontier_v3.pilot.profile";
    private static final String FIXTURE_RUN_ID_PROPERTY = "pale_mirror.frontier_v3.pilot.run_id";

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

    @Test
    void arbitraryPilotPropertiesCannotChangeTheProductionInitialConfiguration() {
        String priorProfile = System.getProperty(FIXTURE_PROFILE_PROPERTY);
        String priorRunId = System.getProperty(FIXTURE_RUN_ID_PROPERTY);
        try {
            System.setProperty(FIXTURE_PROFILE_PROPERTY, "settlement-assault");
            System.setProperty(FIXTURE_RUN_ID_PROPERTY, "an-arbitrary-live-jvm");
            WorldId world = new WorldId("frontier:production-bootstrap");
            var expected = FrontierWorldRuntimeDefinition.configuration(world, 91L);
            var actual = FrontierV3ServerLifecycle.initialConfiguration(world, 91L);
            assertArrayEquals(expected.stateCodec().encode(expected.initialState()), actual.stateCodec().encode(actual.initialState()));
            assertEquals(expected.initialSchedules(), actual.initialSchedules());
        } finally {
            restore(FIXTURE_PROFILE_PROPERTY, priorProfile);
            restore(FIXTURE_RUN_ID_PROPERTY, priorRunId);
        }
    }

    private static void restore(String property, String prior) {
        if (prior == null) System.clearProperty(property);
        else System.setProperty(property, prior);
    }
}
