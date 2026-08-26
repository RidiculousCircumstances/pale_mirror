package io.farfrontier.palemirror.visuals.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.GenesisReadiness;
import org.junit.jupiter.api.Test;

class AuthoredGenesisAdmissionTest {
    @Test
    void normalCampaignRemainsFailClosedUntilAuthoredCatalogIsReady() {
        AuthoredGenesisAdmission.Decision decision = AuthoredGenesisAdmission.resolve(true);

        assertTrue(decision.planAuthoredRegions());
        assertEquals(GenesisReadiness.State.PLANNING, decision.readiness().state());
        assertFalse(decision.readiness().ready(), "normal authored campaigns must retain the admission gate");
    }

    @Test
    void explicitGrayboxModeRecoversAdmissionWithoutStartingTerrainAuthoring() {
        AuthoredGenesisAdmission.Decision decision = AuthoredGenesisAdmission.resolve(false);

        assertFalse(decision.planAuthoredRegions(), "graybox mode must not merely ignore a failed authored planner");
        assertTrue(decision.readiness().ready());
        assertEquals("frontier-graybox", decision.readiness().catalogHash());
        assertTrue(decision.readiness().diagnostic().contains("graybox"));
    }
}
