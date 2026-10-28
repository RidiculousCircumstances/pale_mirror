package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3HarvestHandDepartureTest {
    @Test void savedNativeReceiptAndExactDepartedHandAreRequiredBeforeTheUnknownSceneCanAcknowledge() {
        var base = FrontierV3FixtureCatalog.resourceSiteHarvestConfiguration(new WorldId("frontier:saved-field-hand"), 421L);
        var fixture = HarvestPendingWorkFixture.create(base.initialState().bootstrap(), ResourceFieldCycle.WorkOutcome.HARVESTED);
        var state = fixture.state(); var job = fixture.job(); var lease = fixture.lease();
        var cycle = state.resourceSites().cycle(job.siteId());
        var address = new PhysicalStackAddress.ActorHand(job.workerId(), lease.members().getFirst().entityId());
        var receipt = new ResourceSiteHarvestProgressed(job.siteId(), cycle.epoch(), job.id(), 1, cycle.layout().revision(),
                job.target().cellId(), job.target().generation(), ResourceFieldCycle.WorkOutcome.HARVESTED,
                fixture.due().id(), fixture.due().dueAt().ticks(), Optional.of(
                    new ResourceSiteHarvestProgressed.HandObservation(address, lease.revision(), 1)));
        state = ResourceSiteHarvestProcess.reduceProgressed(state, job.siteId(), receipt);
        var acceptance = state.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow().progress().acceptance().orElseThrow();
        state = ResourceSiteHarvestAcceptanceProcess.acknowledge(state, job.siteId(), new ResourceSiteHarvestWorkAcknowledged(acceptance));
        var resources = state.inventory().fungibleResources().releaseBindings(job.actorAccountId(), lease.revision());
        state = state.withInventory(state.inventory().withFungibleResources(resources))
                .transitionSceneLease(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        job = state.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow();
        lease = state.sceneLeases().get(lease.id());
        var part = ResourceSiteHarvestCargo.part(state, job).orElseThrow();
        var witness = new FrontierV3ResourceSiteHandProjectionWitness(job.siteId(), job.id(), job.actorAccountId(),
                part.id(), job.workerId(), address.entityId(), lease.id(), lease.revision(), cycle.epoch(), 1, 1,
                FrontierV3ResourceSiteHandProjectionWitness.Mode.BIND_RETAINED);
        var body = ActorBodyAuthority.current(state, job.workerId()); var actor = state.actorLocations().get(job.workerId());
        var inactive = FrontierV3ActorCarrierComposition.fromCanonical(state, job.workerId(), actor.kind(),
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, address.entityId(),
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 0L, body.physicalEpoch());
        var departure = new FrontierV3ActorBodyDeparture(inactive, 1L,
                new SceneMemberPosition(job.workerId(), actor.body(), actor.condition().health()), actor.body(),
                actor.condition().health(), FrontierV3ActorBodyDeparture.execution(state, job.workerId()),
                Optional.of(new FrontierV3ActorBodyDeparture.HandStack("minecraft:wheat", 1)), Optional.empty());
        var fingerprint = FrontierV3OperationFingerprint.of(witness.write());
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITIES,
                address.entityId().toString(), 77L);
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_HAND_PROJECTION,
                job.id(), fingerprint, fingerprint, Set.of(participant));
        var operation = FrontierV3OperationReceipt.prepare(declaration, 1L);
        operation = operation.applied(operation.nativeReceipt(participant));
        assertTrue(FrontierV3HarvestHandOperation.savedProjection(state, lease, witness, departure, operation).isEmpty(),
                "APPLIED without a saved native participant must retain the release barrier");
        operation = operation.saved(operation.nativeReceipt(participant));
        var projected = FrontierV3HarvestHandOperation.savedProjection(state, lease, witness, departure, operation).orElseThrow();
        var after = ResourceSiteHarvestProcess.reduceHandProjected(state, job.siteId(), projected);
        assertEquals(SceneLeaseStatus.UNKNOWN_AFTER_RESTART, after.sceneLeases().get(lease.id()).status());
        assertEquals(resources.accounts(), after.inventory().fungibleResources().accounts());
        assertEquals(resources.lots(), after.inventory().fungibleResources().lots());
        assertEquals(state.resourceSites(), after.resourceSites());
        assertFalse(FrontierV3HarvestHandOperation.settledProjection(state, lease, witness, departure));
        assertTrue(FrontierV3HarvestHandOperation.settledProjection(after, lease, witness, departure),
                "canonical acknowledgement permits exact witness cleanup even after native operation retirement");
        assertTrue(FrontierV3HarvestHandOperation.savedProjection(state, lease, witness, departure, null).isEmpty());
        var wrongHand = new FrontierV3ActorBodyDeparture(inactive, 1L, departure.observed(), departure.canonicalBody(),
                departure.canonicalHealth(), departure.executionAtCapture(),
                Optional.of(new FrontierV3ActorBodyDeparture.HandStack("minecraft:wheat", 2)), Optional.empty());
        assertTrue(FrontierV3HarvestHandOperation.savedProjection(state, lease, witness, wrongHand, operation).isEmpty());
        var foreign = new FrontierV3OperationReceipt.Declaration(declaration.protocol(), new SubjectId("job:foreign-hand"),
                fingerprint, fingerprint, Set.of(participant));
        var wrongOwner = new FrontierV3OperationReceipt(foreign, operation.ticket(), 1L, Set.of(participant), Set.of(participant), false, false);
        assertTrue(FrontierV3HarvestHandOperation.savedProjection(state, lease, witness, departure, wrongOwner).isEmpty());
        var changed = new FrontierV3ResourceSiteHandProjectionWitness(witness.siteId(), witness.jobId(), witness.actorAccountId(),
                witness.lotId(), witness.workerId(), witness.entityId(), witness.leaseId(), witness.actorEpoch(), witness.fieldEpoch(),
                witness.accountedPrefix() + 1, witness.quantity(), witness.mode());
        assertTrue(FrontierV3HarvestHandOperation.savedProjection(state, lease, changed, departure, operation).isEmpty());
    }
}
