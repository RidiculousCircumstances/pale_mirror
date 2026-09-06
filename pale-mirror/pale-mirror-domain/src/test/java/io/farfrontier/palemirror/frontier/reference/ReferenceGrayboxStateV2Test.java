package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxStateV2Test {
    @Test
    void restoresCompleteV2OwnerAtEveryPinnedCheckpoint() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        Set<Integer> checkpoints = Set.of(0, 1, 2, 3, 5, 10, 15, 20, 25, 30);
        for (int day = 0; day <= 30; day++) {
            if (checkpoints.contains(day)) {
                Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                        ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
                Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(document.get("reference_state"));
                ReferenceGrayboxStateV2.State v2 = ReferenceGrayboxStateV2.read(reference.get("v2"));
                ReferenceWorld restored = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
                v2.applyTo(restored.v2());
                assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateV2.capture(source)),
                        ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateV2.capture(restored)), "V2 day " + day);
            }
            if (day < 30) source.tick();
        }
    }

    @Test
    void restoredPerceptionContinuesFromItsSavedBeliefsWithoutReplayingGenesis() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(41L));
        source.day(1);
        source.v2().refreshTerritory(source);
        source.v2().observe(source);
        Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
        Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(document.get("reference_state"));
        ReferenceGrayboxStateRoot.State root = ReferenceGrayboxStateRoot.read(reference);
        ReferenceGrayboxStateV2.State v2 = ReferenceGrayboxStateV2.read(reference.get("v2"));
        ReferenceWorld restored = new ReferenceWorld(root.config());
        v2.applyTo(restored.v2());

        source.day(2);
        source.v2().refreshTerritory(source);
        source.v2().observe(source);
        restored.day(2);
        restored.v2().refreshTerritory(restored);
        restored.v2().observe(restored);

        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateV2.capture(source)),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateV2.capture(restored)));
    }

    @Test
    void restoresEveryV2RecordKindInOneNonemptySourceSnapshot() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(41L));
        ReferenceV2State v2 = source.v2();
        String sector = v2.sectors().keySet().iterator().next();
        ReferenceRouteKey route = source.trade().routes().getFirst().key();
        int site = source.resourceSites().keySet().iterator().next();
        v2.mutableHumanPerceptions().get(1).belief(new ReferenceV2Belief(sector, 0, .75d, ReferenceObservationSource.SCOUT,
                .3d, 4.0d, .2d, 1.0d, false));
        v2.mutableHivePerception().belief(new ReferenceV2Belief(sector, 0, .88d, ReferenceObservationSource.TISSUE,
                .3d, 4.0d, .2d, 1.0d, true));
        v2.mutableEmergencyRegimes().put(1, new ReferenceEmergencyRegime(1, ReferenceCivicState.EMERGENCY, 0, 2.0d, 5));
        ReferenceCoalitionCharter charter = new ReferenceCoalitionCharter(1, 1, List.of(1, 2), sector, 0, 5,
                Map.of(1, 2.0d, 2, 2.0d), Map.of(1, 1.0d, 2, 1.0d), Map.of(2, .5d));
        charter.status("active");
        charter.reason("recovery fixture");
        v2.mutableCharters().put(1, charter);
        v2.mutableRouteInsurance().put(route, new ReferenceRouteInsurance(route, 1, .2d, .8d, 3, "active"));
        v2.mutableProcurements().put(1, new ReferenceProcurementOrder(1, 1, ReferenceResource.AMMO, 3.0d, 2.0d, 0, 1.0d, "open"));
        ReferenceCompensationClaim claim = new ReferenceCompensationClaim(1, 1, 1, 2.0d, 3, "fixture");
        claim.status("pending");
        v2.mutableCompensation().put(1, claim);
        v2.mutableCivicSiteProjects().add(new ReferenceCivicSiteProject(1, site, "claim", 2));
        v2.mutableLastCivicWorkDay().put(1, 0);
        v2.mutableChrysalises().put(1, new ReferenceNeuralChrysalis(1, sector, 0, 2, 4.0d));
        v2.mutableHiveLifecycle().put("component:1", ReferenceHiveLifecycle.RECONSTITUTION);
        ReferenceFrontCampaign campaign = new ReferenceFrontCampaign(1, ReferenceFrontCampaignKind.SECTOR_CLEARANCE, 1, List.of(1), sector,
                0, ReferenceFrontPhase.CORDON, Map.of(1, 1.0d), Map.of(1, List.of("resident:1:1")),
                Map.of(1, Map.of(ReferenceHumanUnitKind.LINE, 1.0d)), "recovery fixture");
        campaign.risk(.2d);
        campaign.statusReason("fixture active");
        v2.mutableFrontCampaigns().put(1, campaign);
        v2.mutableSupplyLines().put(1, new ReferenceSupplyLineStatus(1, sector, sector, List.of(sector), true, .1d, .9d, "fixture connected"));
        v2.mutableSectorEngagements().add(new ReferenceSectorEngagement(0, sector, "clear", "human", "tissue", 1.0d, .1d, -.1d, .0d, "fixture"));
        v2.mutableFrontierCooldownUntil().put(1, 4);
        v2.mutableDecisionHistory().add(new ReferenceV2DecisionReceipt(0, 1, "recover", .1d, "fixture", "—"));
        v2.restoreNextIds(2, 2, 2, 2);

        Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
        Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(document.get("reference_state"));
        ReferenceGrayboxStateV2.State state = ReferenceGrayboxStateV2.read(reference.get("v2"));
        ReferenceWorld restored = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(41L));
        state.applyTo(restored.v2());

        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateV2.capture(source)),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateV2.capture(restored)));
    }

    @Test
    void rejectsAnIncompleteV2OwnerBeforeMutatingIt() {
        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxStateV2.read(Map.of()));
    }
}
