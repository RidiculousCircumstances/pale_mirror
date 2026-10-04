package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestSceneReconciliation;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ResourceSiteHarvestSceneReconciliationTest {
    @Test void hotFullPartAtCapacitySettlesExactlyOnceAndRetainsTerminalRecoveryWithoutReplayingCells() {
        var fixture = ResourceSiteHarvestProcessTest.fullBatchAtDepotWithoutFutureCapacity();
        var job = fixture.job();
        var approaching = fixture.state().withActorBody(job.workerId(), fixture.state().resourceSites().cycle(job.siteId())
                .layout().cells().get(job.progress().lastCompletedCropSlotIndex()).workstation().standingBody());
        var candidate = FrontierResourceSiteHarvestSceneSupport.candidate(approaching, job).orElseThrow();
        var lease = SceneLease.forCause(new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:capacity-delivery"),
                approaching.bootstrap().worldId(), new ResourceSiteHarvestSceneCause(job.siteId(), job.id()), candidate.cropSlot(),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(27_000L), 1L, SceneLeaseStatus.PREPARED,
                java.util.List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(approaching.bootstrap().worldId(), job.workerId()))),
                java.util.Set.of(job.workerId()), java.util.Optional.empty());
        var state = ResourceSiteHarvestProcessTest.confirmedPhysicalParticipants(approaching.prepareSceneLease(lease), lease)
                .transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        state = ResourceSiteHarvestProcessTest.inspectGoal(state, job.siteId(), job, lease.id());
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                lease.members().getFirst().entityId()), "minecraft:wheat", 64);
        state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess.reduceHandProjected(state, fixture.site(),
                new ResourceSiteHarvestHandProjected(fixture.site(), job.id(), job.actorAccountId(), lease.id(), lease.revision(), hand));
        state = state.transitionPhysicalIntent(job.intentId(), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING,
                java.util.Optional.empty());
        var depot = job.outputSlot().containerId();
        var stacks = new java.util.ArrayList<FungiblePhysicalObservation.Stack>();
        ReferenceContainerCustody.expectedFungibleSlots(state, depot).forEach((slot, stack) -> stacks.add(
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(depot, slot)), stack.itemKind(), stack.quantity())));
        var resources = state.inventory().fungibleResources();
        resources = resources.rebind(job.depotAccountId(), 1L,
                FungiblePhysicalObservation.bind(resources, job.depotAccountId(), 1L, stacks));
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var replica = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(state, depot), 7L,
                ReferenceContainerCustody.canonicalFingerprint(state, depot), ReferenceContainerCustody.provenance(depot));
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(state.replicaCustody().declare(replica)
                .observe(depot, 7L, 1L, replica.fingerprint(), replica.provenance(), 7L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(depot), depot, ReferenceContainerCustody.PROVIDER_ID,
                        1L, 7L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null))));
        stacks.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(job.outputSlot()), "minecraft:wheat", 64));
        var deliveredResources = ResourceSiteHarvestCargo.deliverObserved(state, job,
                lease.members().getFirst().entityId(), lease.revision(), 1L, stacks);
        var field = state.resourceSites().cycle(job.siteId());
        var expected = state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(deliveredResources))
                .resourceSites(state.resourceSites().replace(state.resourceSites().site(job.siteId()).deliverFullHarvestBatch(job,
                        java.util.Optional.empty(), java.util.Optional.empty(), field,
                        state.actorLocations().get(job.workerId()).supportingSurface()))));
        var receipt = new ResourceSiteHarvestDeliveryObservation(ResourceSiteHarvestBatchDelivered.observationId(job.id(), 0),
                job.intentId(), job.siteId(), job.id(), job.workerId(), job.actorAccountId(), job.depotAccountId(), lease.id(),
                lease.members().getFirst().entityId(), lease.revision(), 64, 1L, stacks,
                ReferenceContainerCustody.canonicalFingerprint(expected, depot), 8L, "witness:capacity-delivery");
        var batch = new ResourceSiteHarvestBatchDelivered(receipt, 0);
        var after = ResourceSitePhysicalIntentStateSupport.deliverHarvestBatch(state, batch);
        assertEquals(field, after.resourceSites().cycle(job.siteId()));
        assertEquals(state.actorLocations(), after.actorLocations(), "resource settlement never moves the resident");
        assertEquals(64, after.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow().deliveredYieldQuantity());
        assertFalse(after.harvestOutputReserves(job.outputSlot()));
        assertFalse(after.inventory().fungibleResources().accounts().containsKey(job.actorAccountId()));
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after));
        assertEquals(after, recovered);
        assertThrows(IllegalArgumentException.class, () -> ResourceSitePhysicalIntentStateSupport.deliverHarvestBatch(recovered, batch));
        var before = state;
        var stale = new ResourceSiteHarvestDeliveryObservation(receipt.id(), receipt.intentId(), receipt.siteId(), receipt.jobId(),
                receipt.workerId(), receipt.actorAccountId(), receipt.depotAccountId(), receipt.leaseId(), receipt.entityId(),
                receipt.actorEpoch() + 1, 64, receipt.depotEpoch(), receipt.depotStacks(), receipt.depotFingerprint(),
                receipt.emittedCanonicalRevision(), receipt.witnessId());
        assertThrows(IllegalArgumentException.class, () -> ResourceSitePhysicalIntentStateSupport.deliverHarvestBatch(before,
                new ResourceSiteHarvestBatchDelivered(stale, 0)));
    }

    @Test void retainedUnboundCargoIsBoundAndResumedAtomicallyWithoutReissuingYield() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        var accepted = oneObservedCrop(hot);
        var job = accepted.resourceSites().site(hot.site()).harvestJobs().values().stream()
                .reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        var resources = accepted.inventory().fungibleResources().releaseBindings(job.actorAccountId(), hot.lease().revision());
        var state = accepted.withInventory(accepted.inventory().withFungibleResources(resources))
                .transitionSceneLease(hot.lease().id(), SceneLeaseStatus.CONFLICT);
        var lease = state.sceneLeases().get(hot.lease().id());
        long epoch = state.fencedRecovery().current().get(
                ActorBodyId.recoveryBindingId(job.workerId())).authorityEpoch();
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                lease.members().getFirst().entityId()), "minecraft:wheat", 1);
        var receipt = new ResourceSiteHarvestSceneReconciled(hot.site(), job.id(), lease.id(), lease.revision(), epoch,
                state.actorLocations().get(job.workerId()).body(), hand);
        var restored = ResourceSiteHarvestSceneReconciliation.reduce(state, hot.site(), receipt);
        var after = restored.inventory().fungibleResources();
        assertEquals(resources.accounts(), after.accounts());
        assertEquals(resources.lots(), after.lots());
        assertEquals(resources.claims(), after.claims());
        assertEquals(state.resourceSites(), restored.resourceSites());
        assertEquals(SceneLeaseStatus.HOT, restored.sceneLeases().get(lease.id()).status());
        assertEquals(1, after.bindings().values().stream().filter(b -> b.accountId().equals(job.actorAccountId())).count());
        assertEquals(restored, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(restored)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestSceneReconciliation.reduce(restored, hot.site(), receipt));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestSceneReconciliation.reduce(state, hot.site(),
                new ResourceSiteHarvestSceneReconciled(hot.site(), job.id(), lease.id(), lease.revision(), epoch,
                        receipt.observedBody(), new FungiblePhysicalObservation.Stack(hand.address(), "minecraft:wheat", 2))));
    }

    @Test void inspectedUntouchedPreparedCropResumesWithoutCreditingOrReplayingYield() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        var state = oneObservedCrop(hot);
        var job = (ResourceSiteHarvestJob) state.resourceSites().site(hot.site()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        var goal = ResourceSiteHarvestGoal.current(state, job);
        state = ResourceSiteHarvestProcessTest.inspectGoal(state, hot.site(), job, hot.lease().id());
        state = ResourceSiteHarvestProcessTest.completeLabourHot(state, hot.site(), hot.lease().id());
        state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess.reduceCropPrepared(state, hot.site(),
                new ResourceSiteHarvestCropPrepared(job.id(), goal.nextWorkSlot(), job.target().generation()));
        state = state.transitionSceneLease(hot.lease().id(), SceneLeaseStatus.CONFLICT);
        var lease = state.sceneLeases().get(hot.lease().id());
        long epoch = state.fencedRecovery().current().get(
                ActorBodyId.recoveryBindingId(job.workerId())).authorityEpoch();
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                lease.members().getFirst().entityId()), "minecraft:wheat", 1);
        var receipt = new ResourceSiteHarvestSceneReconciled(hot.site(), job.id(), lease.id(), lease.revision(), epoch,
                goal.representative().standingBody(), hand);
        var restored = ResourceSiteHarvestSceneReconciliation.reduce(state, hot.site(), receipt);
        assertEquals(SceneLeaseStatus.HOT, restored.sceneLeases().get(lease.id()).status());
        assertEquals(state.resourceSites(), restored.resourceSites());
        assertEquals(state.inventory(), restored.inventory());
        assertTrue(((ResourceSiteHarvestJob) restored.resourceSites().site(hot.site()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow())
                .progress().hasPendingCrop());
        var isolated = state;
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestSceneReconciliation.reduce(isolated, hot.site(),
                new ResourceSiteHarvestSceneReconciled(hot.site(), job.id(), lease.id(), lease.revision(), epoch,
                        goal.representative().standingBody(), new FungiblePhysicalObservation.Stack(hand.address(), "minecraft:wheat", 2))));
        var body = goal.representative().standingBody();
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestSceneReconciliation.reduce(isolated, hot.site(),
                new ResourceSiteHarvestSceneReconciled(hot.site(), job.id(), lease.id(), lease.revision(), epoch,
                        new BodyPosition(body.x() + 1, body.y(), body.z()), hand)));
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void exactInspectedBatchResumesSameWorkerAndEpochWithoutReplayingHarvest(boolean terminal) {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        var complete = terminal
                ? ResourceSiteHarvestProcessTest.completeHarvestWorkHot(hot.state(), hot.site(), hot.job(), hot.lease().id())
                : oneObservedCrop(hot);
        var state = complete.transitionSceneLease(hot.lease().id(), SceneLeaseStatus.CONFLICT);
        var job = (ResourceSiteHarvestJob) state.resourceSites().site(hot.site()).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        var lease = state.sceneLeases().get(hot.lease().id());
        var bindingId = ActorBodyId.recoveryBindingId(job.workerId());
        long epoch = state.fencedRecovery().current().get(bindingId).authorityEpoch();
        var original = state.actorLocations().get(job.workerId()).body();
        var displaced = new BodyPosition(original.x() + 1, original.y(), original.z());
        var hand = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                lease.members().getFirst().entityId()), "minecraft:wheat", terminal ? 64 : 1);
        var receipt = new ResourceSiteHarvestSceneReconciled(hot.site(), job.id(), lease.id(), lease.revision(), epoch, displaced, hand);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(receipt, codecs.decode(receipt.type(), codecs.encode(receipt)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestSceneReconciliation.reduce(state, hot.site(), receipt),
                "a field receipt cannot manufacture common body permission or change its position");
        var inspected = ActorBodyAuthority.inspected(state,
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected(
                        ActorBodyAuthority.current(state, job.workerId()),
                        io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.INDEXED_LIVING, original,
                        state.actorLocations().get(job.workerId()).condition().health(), displaced,
                        state.actorLocations().get(job.workerId()).condition().health(),
                        state.actorExecutions().actors().get(job.workerId()).current()));
        var restored = ResourceSiteHarvestSceneReconciliation.reduce(inspected, hot.site(), receipt);
        assertEquals(SceneLeaseStatus.HOT, restored.sceneLeases().get(lease.id()).status());
        assertEquals(displaced, restored.actorLocations().get(job.workerId()).body());
        assertEquals(epoch, restored.fencedRecovery().current().get(bindingId).authorityEpoch());
        assertEquals(FencedRecoveryPhase.RUNNING, restored.fencedRecovery().current().get(bindingId).phase());
        assertEquals(state.inventory(), restored.inventory());
        assertEquals(state.resourceSites(), restored.resourceSites());
        assertEquals(restored, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(restored)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestSceneReconciliation.reduce(restored, hot.site(), receipt));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestSceneReconciliation.reduce(state, hot.site(),
                new ResourceSiteHarvestSceneReconciled(hot.site(), job.id(), lease.id(), lease.revision(), epoch + 1, displaced, hand)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestSceneReconciliation.reduce(state, hot.site(),
                new ResourceSiteHarvestSceneReconciled(hot.site(), job.id(), lease.id(), lease.revision() + 1, epoch, displaced, hand)));
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestSceneReconciliation.reduce(state, hot.site(),
                new ResourceSiteHarvestSceneReconciled(hot.site(), job.id(), lease.id(), lease.revision(), epoch, displaced,
                    new FungiblePhysicalObservation.Stack(hand.address(), "minecraft:wheat", 63))));
    }

    private static FrontierWorldState oneObservedCrop(ResourceSiteHarvestProcessTest.HotHarvest hot) {
        var job = hot.job();
        var state = hot.state();
        var goal = ResourceSiteHarvestGoal.current(state, job);
        if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, job))
            state = ResourceSiteHarvestProcessTest.inspectGoal(state, hot.site(), job, hot.lease().id());
        state = ResourceSiteHarvestProcessTest.completeLabourHot(state, hot.site(), hot.lease().id());
        state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess.reduceCropPrepared(state, hot.site(),
                new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex(), job.target().generation()));
        var cycle = state.resourceSites().cycle(hot.site());
        var cell = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
        var due = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess.coldProgress(job, 22_301L);
        return io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess.reduceProgressed(state, hot.site(),
                new ResourceSiteHarvestProgressed(hot.site(), cycle.epoch(), job.id(), 1, cycle.layout().revision(), cell, job.target().generation(),
                        ResourceFieldCycle.WorkOutcome.HARVESTED, due.id(), due.dueAt().ticks(), java.util.Optional.of(
                        new ResourceSiteHarvestProgressed.HandObservation(new PhysicalStackAddress.ActorHand(job.workerId(),
                                hot.lease().members().getFirst().entityId()), hot.lease().revision(), 1))));
    }
}
