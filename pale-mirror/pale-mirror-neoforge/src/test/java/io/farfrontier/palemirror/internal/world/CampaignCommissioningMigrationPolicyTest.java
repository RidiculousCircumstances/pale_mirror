package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.internal.adapter.RailConstructionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignCommissioningMigrationPolicyTest {
    @Test
    void untouchedPlanBecomesLoadedChunksOnly() {
        var decision = CampaignCommissioningMigrationPolicy.classify(
                CampaignCommissioningStatus.PLANNED, false, "");
        assertEquals(RailConstructionPolicy.LOADED_CHUNKS_ONLY, decision.policy());
        assertEquals(CampaignCommissioningStatus.PLANNED, decision.status());
    }

    @Test
    void partialPhysicalLineFailsClosed() {
        var decision = CampaignCommissioningMigrationPolicy.classify(
                CampaignCommissioningStatus.RAIL_BUILDING, true, "legacy");
        assertEquals(RailConstructionPolicy.LEGACY, decision.policy());
        assertEquals(CampaignCommissioningStatus.SUSPENDED, decision.status());
        assertTrue(decision.diagnostic().contains("explicit operator recovery"));
    }

    @Test
    void commissionedServiceRemainsLegacy() {
        var decision = CampaignCommissioningMigrationPolicy.classify(
                CampaignCommissioningStatus.TRAIN_COMMISSIONING, true, "running");
        assertEquals(RailConstructionPolicy.LEGACY, decision.policy());
        assertEquals(CampaignCommissioningStatus.TRAIN_COMMISSIONING, decision.status());
    }
}
