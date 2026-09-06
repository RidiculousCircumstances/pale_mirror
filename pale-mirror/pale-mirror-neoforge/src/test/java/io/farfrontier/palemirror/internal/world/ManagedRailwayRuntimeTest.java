package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.internal.adapter.FreightServiceStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedRailwayRuntimeTest {
    @Test
    void onlyProviderProgressCanResumeBlockedFreightCommissioning() {
        assertTrue(RailwayRecoveryPolicy.serviceCanResume(FreightServiceStatus.PLACING));
        assertTrue(RailwayRecoveryPolicy.serviceCanResume(FreightServiceStatus.RUNNING));
        assertTrue(RailwayRecoveryPolicy.serviceCanResume(FreightServiceStatus.PLAYER_MANAGED));
        assertFalse(RailwayRecoveryPolicy.serviceCanResume(FreightServiceStatus.BLOCKED));
        assertFalse(RailwayRecoveryPolicy.serviceCanResume(FreightServiceStatus.UNAVAILABLE));
    }
}
