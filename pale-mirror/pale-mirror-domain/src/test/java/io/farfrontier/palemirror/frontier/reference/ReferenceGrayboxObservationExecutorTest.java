package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxObservationExecutorTest {
    @Test
    void exactSettlementDeathAppliesOnceAndAReplayIsRejectedAsStale() {
        ReferenceWorld world = grayboxWorld();
        ReferenceSettlement settlement = world.settlements().get(1);
        String residentId = settlement.residents().availableIds().getFirst();
        String revision = ReferenceGrayboxProjection.from(world).stateRevision();

        ReferenceGrayboxObservationOutcome first = world.observe(
                ReferenceGrayboxResidentObservation.killed("physical:resident:death:1", revision, residentId));

        assertTrue(first.applied());
        assertEquals(ReferenceGrayboxObservationOutcome.Status.APPLIED, first.status());
        assertFalse(settlement.residents().livingIds().contains(residentId));
        assertEquals(settlement.population(), (double) settlement.residents().size());
        assertFalse(revision.equals(first.stateRevision()));
        assertEquals(ReferenceGrayboxObservationOutcome.Status.REJECTED_STALE,
                world.observe(ReferenceGrayboxResidentObservation.killed("physical:resident:death:1", revision, residentId)).status());
        world.assertProfileInvariants();
    }

    @Test
    void exactOperationWoundMovesOnlyThatNamedResidentToTheOperationWoundedLane() {
        ReferenceWorld world = grayboxWorld();
        ReferenceOperation operation = world.operations().launchHuman(world, ReferenceOperationKind.RECON, 1,
                ReferenceTargetRef.cell(10, 10));
        String residentId = operation.residentIdsBySettlement().get(1).getFirst();
        String revision = ReferenceGrayboxProjection.from(world).stateRevision();

        ReferenceGrayboxObservationOutcome outcome = world.observe(
                new ReferenceGrayboxResidentObservation(1, "physical:resident:wound:1", revision, residentId,
                        ReferenceGrayboxResidentObservation.Kind.WOUNDED));

        assertTrue(outcome.applied());
        assertFalse(operation.residentIdsBySettlement().get(1).contains(residentId));
        assertTrue(operation.woundedResidentIdsBySettlement().get(1).contains(residentId));
        assertEquals(ReferenceResidentCondition.WOUNDED, world.settlements().get(1).residents().resident(residentId).condition());
        assertEquals(1.0d, operation.personnel());
        world.assertProfileInvariants();
    }

    @Test
    void staleObservationLeavesTheCanonicalResidentUntouched() {
        ReferenceWorld world = grayboxWorld();
        ReferenceSettlement settlement = world.settlements().get(1);
        String residentId = settlement.residents().availableIds().getFirst();

        ReferenceGrayboxObservationOutcome outcome = world.observe(
                ReferenceGrayboxResidentObservation.killed("physical:resident:stale", "0".repeat(64), residentId));

        assertEquals(ReferenceGrayboxObservationOutcome.Status.REJECTED_STALE, outcome.status());
        assertTrue(settlement.residents().livingIds().contains(residentId));
        world.assertProfileInvariants();
    }

    @Test
    void exactFieldPostDeathUpdatesOnlyTheRegisteredGarrison() {
        ReferenceWorld world = grayboxWorld();
        ReferenceSettlement settlement = world.settlements().get(1);
        List<String> garrison = settlement.residents().availableIds().subList(0, 4);
        settlement.assignPeopleToFieldPost(garrison, 1);
        ReferenceFieldCampaign campaign = world.field().createCampaign(world, ReferenceCampaignKind.CONTAINMENT, settlement.id(), Set.of(),
                "cell", null, 10, 10, "physical-observation-test");
        ReferenceFieldPost post = world.field().startPost(world, campaign.id(), ReferenceFieldPostKind.CHECKPOINT, 1, 1, settlement.id(),
                Set.of(settlement.id()), Map.of(settlement.id(), 4.0d), Map.of(), Map.of(settlement.id(), garrison));
        String residentId = garrison.getFirst();
        String revision = ReferenceGrayboxProjection.from(world).stateRevision();

        ReferenceGrayboxObservationOutcome outcome = world.observe(
                ReferenceGrayboxResidentObservation.killed("physical:field-post:death", revision, residentId));

        assertTrue(outcome.applied());
        assertEquals(3.0d, post.garrison());
        assertFalse(post.residentIdsBySettlement().get(settlement.id()).contains(residentId));
        assertFalse(settlement.residents().livingIds().contains(residentId));
        world.assertProfileInvariants();
    }

    @Test
    void exactFrontCampaignDeathKeepsTheRemainingNamedForceInOneCustodyLane() {
        ReferenceWorld world = grayboxWorld();
        ReferenceSettlement settlement = world.settlements().get(1);
        List<String> deployed = settlement.deployPeople(1_000_001,
                Map.of(ReferenceHumanUnitKind.LINE.id(), 3)).get(ReferenceHumanUnitKind.LINE.id());
        ReferenceFrontCampaign campaign = new ReferenceFrontCampaign(1, ReferenceFrontCampaignKind.CORDON, settlement.id(),
                List.of(settlement.id()), "0:0", world.day(), ReferenceFrontPhase.CORDON,
                Map.of(settlement.id(), 3.0d), Map.of(settlement.id(), deployed),
                Map.of(settlement.id(), Map.of(ReferenceHumanUnitKind.LINE, 3.0d)), "physical-observation-test");
        world.v2().mutableFrontCampaigns().put(campaign.id(), campaign);
        String residentId = deployed.getFirst();
        String revision = ReferenceGrayboxProjection.from(world).stateRevision();

        ReferenceGrayboxObservationOutcome outcome = world.observe(
                ReferenceGrayboxResidentObservation.killed("physical:front:death", revision, residentId));

        assertTrue(outcome.applied());
        assertEquals(2.0d, campaign.personnel());
        assertFalse(campaign.residentIdsBySettlement().get(settlement.id()).contains(residentId));
        assertFalse(settlement.residents().livingIds().contains(residentId));
        world.assertProfileInvariants();
    }

    @Test
    void exactBioformDeathRetainsTheOtherZombieIdsAndRescalesOnlyThatSwarm() {
        ReferenceWorld world = grayboxWorld();
        ReferenceSwarm swarm = new ReferenceSwarm(901, 12.0d, 13.0d, 90.0d, -1, .9d,
                ReferenceBioformKind.RAIDER, Map.of(ReferenceBioformKind.RAIDER, 2.0d, ReferenceBioformKind.BREAKER, 1.0d),
                ReferenceFormationPhase.SCREEN, 1.0d, null, null, null, false);
        world.infection().swarms.add(swarm);
        String bioformId = "bioform:901:raider:1";
        String revision = ReferenceGrayboxProjection.from(world).stateRevision();

        ReferenceGrayboxObservationOutcome outcome = world.observe(
                ReferenceGrayboxBioformObservation.killed("physical:bioform:death", revision, bioformId));

        assertTrue(outcome.applied());
        assertFalse(swarm.hasExactBioform(bioformId));
        assertEquals(60.0d, swarm.power());
        assertEquals(Map.of(ReferenceBioformKind.RAIDER, 1.0d, ReferenceBioformKind.BREAKER, 1.0d), swarm.composition());
        assertFalse(ReferenceGrayboxProjection.from(world).bioforms().stream().map(ReferenceGrayboxSnapshot.Bioform::id).toList()
                .contains(bioformId));
        assertTrue(ReferenceGrayboxProjection.from(world).bioforms().stream().map(ReferenceGrayboxSnapshot.Bioform::id).toList()
                .contains("bioform:901:raider:2"));
        assertEquals(ReferenceGrayboxObservationOutcome.Status.REJECTED_STALE,
                world.observe(ReferenceGrayboxBioformObservation.killed("physical:bioform:death", revision, bioformId)).status());
        world.assertProfileInvariants();
    }

    private static ReferenceWorld grayboxWorld() {
        return new ReferenceWorld(ReferenceWorldConfig.graybox1To40(7L));
    }
}
