package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HarvestRetiredEffectTest {
    @Test void retainedCellSettlesWithoutResurrectingWorkerOrConflictingField() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        var job = hot.state().resourceSites().site(hot.site()).harvestJobs().values().stream()
                .reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        var state = ResourceSiteHarvestProcessTest.inspectGoal(hot.state(), hot.site(), job, hot.lease().id());
        state = ResourceSiteHarvestProcessTest.completeLabourHot(state, hot.site(), hot.lease().id());
        job = state.resourceSites().site(hot.site()).harvestJob(job.id()).orElseThrow();
        state = ResourceSiteHarvestProcess.reduceCropPrepared(state, hot.site(),
                new ResourceSiteHarvestCropPrepared(job.id(), job.progress().nextCropSlotIndex(), job.target().generation()));
        var identity = new ActorActuationId(ActorBodyAuthority.current(state, job.workerId()),
                HarvestDeathResources.execution(state, job.workerId()).orElseThrow());
        var due = ResourceSiteHarvestProcess.coldProgress(job, job.progress().work().orElseThrow().evaluatedAtTick() + 1);
        var field = state.resourceSites().cycle(hot.site());
        var before = state;
        var dead = ActorBodyAuthority.died(state, ModeledActorBodyFacts.death(state, job.workerId(),
                state.actorLocations().get(job.workerId()).body(), "fixture:field-death"),
                FrontierActorDeathConsequences.INSTANCE, due.dueAt().ticks());
        assertEquals(ResourceSitePhase.HARVESTING, dead.resourceSites().site(hot.site()).phase());
        assertEquals(before.strategicPlans(), dead.strategicPlans(), "one worker death must not block the shared field task");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Consumed.class,
                ResourceSiteHarvestProcess.planColdProgress(dead, due).getFirst().payload());
        assertTrue(ActorInventoryInteractionFences.pending(dead, job.workerId()));
        for (boolean applied : java.util.List.of(false, true)) {
            var hand = new ResourceSiteHarvestProgressed.HandObservation(new PhysicalStackAddress.ActorHand(job.workerId(),
                    hot.lease().members().getFirst().entityId()), hot.lease().revision(), applied ? 1 : 0);
            var effect = new ResourceSiteHarvestProgressed(hot.site(), field.epoch(), job.id(), job.progress().completedCropSlots() + 1,
                    field.layout().revision(), job.target().cellId(), job.target().generation(), ResourceFieldCycle.WorkOutcome.HARVESTED,
                    due.id(), due.dueAt().ticks(), Optional.of(hand));
            var receipt = new HarvestRetiredEffectObserved(identity, effect, applied);
            var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
            assertEquals(receipt, codecs.decode(receipt.type(), codecs.encode(receipt)));
            assertThrows(IllegalArgumentException.class, () -> apply(before, receipt, due.dueAt().ticks()));
            var next = apply(dead, receipt, due.dueAt().ticks());
            var retained = next.resourceSites().site(hot.site()).harvestJob(job.id()).orElseThrow();
            assertFalse(retained.progress().hasPendingCrop());
            assertEquals(applied ? 1 : 0, ResourceSiteHarvestCargo.quantity(next, retained));
            assertEquals(job.progress().selectedCropSlotIndex(), retained.progress().selectedCropSlotIndex());
            assertEquals(applied, retained.progress().acceptance().isPresent());
            assertEquals(dead.actorLocations(), next.actorLocations());
            assertEquals(dead.actorExecutions(), next.actorExecutions());
            assertEquals(ResourceSitePhase.HARVESTING, next.resourceSites().site(hot.site()).phase());
            var observed = next;
            assertThrows(IllegalArgumentException.class, () -> apply(observed, receipt, due.dueAt().ticks()));
            var codec = new FrontierWorldStateCodec(); assertEquals(next, codec.decode(codec.encode(next)));
            var ground = applied ? Optional.of(new ResourceCustody.Ground(java.util.UUID.nameUUIDFromBytes(
                    "harvest-death".getBytes(java.nio.charset.StandardCharsets.UTF_8)), new BlockPosition(0, 64, 0)))
                    : Optional.<ResourceCustody.Ground>empty();
            var settled = new HarvestDeathSettled(job.siteId(), identity, retained.progress().completedCropSlots(),
                    retained.harvestedYieldQuantity(), retained.deliveredYieldQuantity(), applied ? hand.authorityEpoch() : 0, ground);
            assertEquals(settled, codecs.decode(settled.type(), codecs.encode(settled)));
            if (applied) {
                assertThrows(IllegalArgumentException.class, () -> apply(observed, settled, due.dueAt().ticks()));
                // Modeled positive witness retirement. Native acceptance is a separate check.
                next = apply(next, new ResourceSiteHarvestWorkAcknowledged(retained.progress().acceptance().orElseThrow()), due.dueAt().ticks());
            }
            var ready = next;
            var closed = apply(ready, settled, due.dueAt().ticks());
            assertTrue(closed.resourceSites().site(job.siteId()).harvestJob(job.id()).isEmpty());
            assertTrue(HarvestDeathResources.execution(closed, job.workerId()).isEmpty());
            assertFalse(closed.physicalIntents().containsKey(job.intentId()));
            assertEquals(ready.resourceSites().cycle(job.siteId()), closed.resourceSites().cycle(job.siteId()));
            assertEquals(ready.strategicPlans(), closed.strategicPlans());
            assertEquals(ready.inventory().fungibleResources().lots(), closed.inventory().fungibleResources().lots());
            if (applied) assertTrue(closed.inventory().fungibleResources().accounts().values().stream()
                    .anyMatch(account -> account.custody().equals(ground.orElseThrow()) && account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum() == 1));
            assertEquals(closed, codec.decode(codec.encode(closed)));
            assertThrows(IllegalArgumentException.class, () -> apply(closed, settled, due.dueAt().ticks()));
        }
    }
    private static FrontierWorldState apply(FrontierWorldState state, FrontierPayload payload, long tick) {
        var id = new CommandId("command:field-retired-effect");
        var command = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, state.bootstrap().worldId(), new Revision(100),
                new SimInstant(tick), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload);
        var plan = FrontierWorldRuntimeDefinition.planCommand(state, command);
        if (!(plan instanceof CommandPlan.Accepted accepted)) throw new IllegalArgumentException("field retired effect rejected: " + plan);
        return GoodsTradeTest.applyEvents(state, accepted.events(), tick, "resource-sites");
    }
}
