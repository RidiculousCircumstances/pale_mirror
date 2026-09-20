package io.farfrontier.palemirror.internal.frontier.v3.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3PilotAnchorResolutionDeadlineTest {
    @Test
    void usesClientMonotonicTimeForOneRecoveredAnchorRequest() {
        long started = 9_000_000_000L;
        assertTrue(FrontierV3PilotAnchorResolutionDeadline.pollDue(started, -1L));
        assertFalse(FrontierV3PilotAnchorResolutionDeadline.pollDue(started + 999_999_999L, started));
        assertTrue(FrontierV3PilotAnchorResolutionDeadline.pollDue(started + 1_000_000_000L, started));
        assertFalse(FrontierV3PilotAnchorResolutionDeadline.timedOut(started + 4_999_999_999L, started, 5_000L));
        assertTrue(FrontierV3PilotAnchorResolutionDeadline.timedOut(started + 5_000_000_000L, started, 5_000L));
        assertFalse(FrontierV3PilotAnchorResolutionDeadline.timedOut(started + 119_999_999_999L, started, 120_000L));
        assertTrue(FrontierV3PilotAnchorResolutionDeadline.timedOut(started + 120_000_000_000L, started, 120_000L));
    }
}
