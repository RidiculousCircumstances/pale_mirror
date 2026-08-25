package io.farfrontier.palemirror.frontier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrontierCampaignTest {
    @Test
    void campaignUsesTheReadableFirstWindowThenTheReferenceRetryCadence() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 115L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();

        commands.execute(state, new FrontierCommand.AdvanceDays(35, "test:before-first-window"));
        assertTrue(state.campaigns().isEmpty(), "a graybox must expose an initial hive approach before its first coalition decision");

        commands.execute(state, new FrontierCommand.AdvanceDays(1, "test:first-window"));
        FrontierCampaign campaign = state.campaigns().stream().findFirst().orElseThrow();
        assertEquals(36L, campaign.startedDay());
        assertTrue(campaign.supplyReadinessPermille() >= FrontierBalance.CAMPAIGN_MINIMUM_READINESS_PERMILLE,
                "the first authorised front must meet the reference 48% supplied-front threshold");
        assertEquals(480, FrontierBalance.CAMPAIGN_MINIMUM_READINESS_PERMILLE,
                "the Java threshold is the 0.48 pale_mirror_ai frontier value in permille");

        commands.execute(state, new FrontierCommand.AdvanceDays(4, "test:reference-retry"));
        assertTrue(state.campaign(campaign.id()).isPresent(),
                "the four-day reference planning pass must preserve the existing canonical commitment rather than recreate it");
    }

    @Test
    void campaignContractUsesReferencePhasesAndScaledRaidNestRequirements() {
        assertEquals(java.util.List.of("RECON", "ASSEMBLE", "ESTABLISH", "CORDON", "CLEAR", "HOLD", "RESTORE", "WITHDRAW",
                        "COMPLETE", "FAILED"),
                java.util.Arrays.stream(FrontierCampaign.Phase.values()).map(Enum::name).toList(),
                "the persisted Java phase vocabulary must stay aligned with pale_mirror_ai FrontPhase");
        assertEquals(4, FrontierBalance.CAMPAIGN_PLANNING_INTERVAL_DAYS);
        assertEquals(18, FrontierBalance.CAMPAIGN_HOLD_DAYS);
        assertEquals(8, FrontierBalance.CAMPAIGN_RESTORE_DAYS);
        assertEquals(1, FrontierBalance.campaignPersonnel(FrontierProfile.GRAYBOX_10));
        assertEquals(2L, FrontierBalance.campaignFood(FrontierProfile.GRAYBOX_10));
        assertEquals(1L, FrontierBalance.campaignMedicine(FrontierProfile.GRAYBOX_10));
        assertEquals(1L, FrontierBalance.campaignWeapons(FrontierProfile.GRAYBOX_10));
        assertEquals(2L, FrontierBalance.campaignAmmo(FrontierProfile.GRAYBOX_10));
    }

    @Test
    void suppliedCoalitionUsesRealGuardsAndPhysicalLossImmediatelyFailsAnUnviableCampaign() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 115L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();

        commands.execute(state, new FrontierCommand.AdvanceDays(36, "test:campaign-launch"));
        FrontierCampaign campaign = state.campaigns().stream().findFirst().orElseThrow();
        assertEquals(FrontierCampaign.Phase.RECON, campaign.phase());
        assertTrue(campaign.contributorSettlementIds().size() >= 2);
        assertTrue(campaign.participantIds().stream().allMatch(id -> state.resident(id).orElseThrow().role() == FrontierResidentRole.GUARD));

        commands.execute(state, new FrontierCommand.AdvanceDays(2, "test:campaign-depart"));
        assertEquals(FrontierCampaign.Phase.ESTABLISH, campaign.phase());
        assertTrue(campaign.participantIds().stream().allMatch(state::residentIsDeployed));
        FrontierCommandOutcome finalLoss = null;
        for (String participantId : campaign.participantIds().stream().limit(2).toList()) {
            FrontierResident participant = state.resident(participantId).orElseThrow();
            finalLoss = commands.execute(state, new FrontierCommand.ApplyObservation(new FrontierPhysicalObservation.ResidentDeath(
                    "test:campaign-loss:" + participantId, participantId, participant.materializationId(), participant.revision(), "player:test")));
        }

        assertEquals(FrontierCampaign.Phase.FAILED, state.campaign(campaign.id()).orElseThrow().phase());
        assertTrue(finalLoss.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.CAMPAIGN_RESOLVED
                && value.subjectId().equals(campaign.id())));
    }

    @Test
    void suppliedClearanceUsesTheCanonicalCoreDestructionConsequences() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 115L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();

        commands.execute(state, new FrontierCommand.AdvanceDays(36, "test:campaign-launch"));
        FrontierCampaign campaign = state.campaigns().stream().findFirst().orElseThrow();
        FrontierCommandOutcome clearance = commands.execute(state, new FrontierCommand.AdvanceDays(
                campaign.transitDays() + 5, "test:campaign-clearance"));

        assertEquals(FrontierHiveOrgan.State.DESTROYED, state.hiveOrgan(campaign.targetOrganId()).orElseThrow().state());
        assertEquals(FrontierHive.State.DECAPITATED, state.hive(campaign.targetHiveId()).orElseThrow().state());
        assertTrue(state.assaults().stream().filter(value -> value.hiveId().equals(campaign.targetHiveId()))
                .allMatch(FrontierAssault::terminal));
        assertTrue(clearance.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.HIVE_ORGAN_DESTROYED
                && value.subjectId().equals(campaign.targetOrganId())));
    }
}
