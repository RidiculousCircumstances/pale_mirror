package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FieldEquipmentTest {
    @Test void preparedEquipmentFencesLivingBodyAndAbortsOnlyTheExactUnusedGeneration() {
        var fixture = ResourceSiteHarvestProcessTest.awaitingEquipment(125L);
        var job = fixture.job(); var state = fixture.state();
        state = ActorBodyAuthority.demand(state, job.workerId());
        var body = ActorBodyAuthority.current(state, job.workerId());
        state = ActorBodyAuthority.running(state, body);
        var execution = state.actorExecutions().current(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST).get(job.workerId());
        var item = state.inventory().items().get(job.equipment().itemId());
        var step = new WorkEquipmentStep(new io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation(
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(body, execution), job.storageRevision()),
                item, (InventoryCustody.ContainerSlot) item.custody(), 1L);
        var prepared = state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(job.siteId())
                .withHarvestEquipment(job, job.equipment().prepare(step))));
        assertTrue(ActorInventoryInteractionFences.pending(prepared, job.workerId()));
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.released(prepared, body));
        var abort = new FieldEquipmentChanged(job.siteId(), job.id(), job.equipment().stage(),
                FieldEquipmentChanged.Action.ABORT_UNATTEMPTED, Optional.of(step));
        var codecs = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(abort, codecs.decode(abort.type(), codecs.encode(abort)));
        var restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(prepared));
        var after = FieldEquipmentProcess.reduce(restored, job.siteId(), abort, 22_100);
        assertEquals(prepared.inventory(), after.inventory(), "an unused effect cannot award or discard an item");
        var next = after.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow();
        assertTrue(next.equipment().pending().isEmpty()); assertEquals(job.storageRevision() + 1, next.storageRevision());
        assertFalse(ActorInventoryInteractionFences.pending(after, job.workerId()));
        assertThrows(IllegalArgumentException.class, () -> FieldEquipmentProcess.reduce(after, job.siteId(), abort, 22_100));
        // Reproduce the old partial departure: physical authority retired before
        // the still-unissued preparation was retained. Only that incarnation drains.
        var retired = ActorBodyAuthority.released(state, body);
        var stranded = retired.withResourceSites(retired.resourceSites().replace(retired.resourceSites().site(job.siteId())
                .withHarvestEquipment(job, job.equipment().prepare(step))));
        assertDoesNotThrow(() -> FieldEquipmentProcess.reduce(stranded, job.siteId(), abort, 22_100));
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.demand(stranded, job.workerId()),
                "do not create another incarnation while a retired physical transfer is unresolved");
    }

    @Test void playerRemovalReleasesOnlyTheUnstartedOfferWithoutChangingAnyCell() {
        var fixture = ResourceSiteHarvestProcessTest.awaitingEquipment(125L);
        var job = fixture.job(); var before = fixture.state();
        var tool = before.inventory().items().get(job.equipment().itemId());
        var player = new InventoryCustody.Player(new java.util.UUID(0, 228));
        var changed = new ExactItemCustodyChanged(tool.id(), tool.custody(), player);
        var after = FrontierWorldEventReducer.reduce(before, new FrontierEvent(1, new EventId("test:tool-removal"),
                new TransactionId("test:tool-removal"), before.bootstrap().worldId(), new Revision(1), new SimInstant(22_100),
                tool.economicOwnerId(), CauseChain.root(new CommandId("test:tool-removal")), changed),
                io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.processRegistry());
        var cancelled = after.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow();
        assertEquals(WorkToolAssignment.Stage.RELEASED, cancelled.equipment().stage());
        assertTrue(cancelled.progress().complete()); assertEquals(0, cancelled.harvestedYieldQuantity());
        assertEquals(player, after.inventory().items().get(tool.id()).custody());
        assertEquals(player, ExactItemReadIndex.owned(after.inventory(), tool.economicOwnerId(), tool.itemKind()).stream()
                .filter(item -> item.id().equals(tool.id())).findFirst().orElseThrow().custody());
        assertEquals(tool, ExactItemReadIndex.owned(before.inventory(), tool.economicOwnerId(), tool.itemKind()).stream()
                .filter(item -> item.id().equals(tool.id())).findFirst().orElseThrow(), "old immutable branch must retain its own custody");
        assertFalse(WorkEquipmentSelection.available(after, tool.economicOwnerId(), after.inventory().items().get(tool.id()),
                FieldEquipment.requirements(), java.util.Set.of()), "index membership grants no work availability");
        assertEquals(before.resourceSites().cycle(job.siteId()), after.resourceSites().cycle(job.siteId()));
        assertFalse(WorkEquipmentClaimComposition.reserved(after).contains(tool.id()));
    }
    @Test void acquisitionRetainsTheExactItemAndDuplicateTransferCannotEquipItTwice() {
        var fixture = ResourceSiteHarvestProcessTest.awaitingEquipment(125L);
        var job = fixture.job(); var before = fixture.state();
        assertEquals(ResourceSiteHarvestGoal.Kind.EQUIPMENT_SERVICE, ResourceSiteHarvestGoal.current(before, job).kind());
        var event = new FieldEquipmentChanged(job.siteId(), job.id(), WorkToolAssignment.Stage.ACQUIRING,
                FieldEquipmentChanged.Action.COLD_TRANSFER, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> FieldEquipmentProcess.reduce(before, job.siteId(), event, 22_100));
        var atSource = before.withActorBody(job.workerId(), job.equipment().source().station().standingBody());
        var after = FieldEquipmentProcess.reduce(atSource, job.siteId(), event, 22_100);
        var held = after.inventory().items().get(job.equipment().itemId());
        assertEquals(new InventoryCustody.Actor(job.workerId()), held.custody());
        assertEquals(before.inventory().items().get(held.id()).condition(), held.condition());
        assertEquals(WorkToolAssignment.Stage.EQUIPPED, after.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow().equipment().stage());
        assertThrows(IllegalArgumentException.class, () -> FieldEquipmentProcess.reduce(after, job.siteId(), event, 22_100));
        var codec = new FrontierWorldStateCodec();
        assertEquals(after, codec.decode(codec.encode(after)));
    }

    @Test void finalUsefulOperationWearsOnceReturnsToolAndLeavesOtherCellsAvailable() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var job = fixture.job(); var state = fixture.state();
        var tool = state.inventory().items().get(job.equipment().itemId());
        state = state.withInventory(state.inventory().changeCondition(tool,
                new ItemCondition(tool.condition().maximumDamage(), tool.condition().maximumDamage() - 2)));
        var due = ResourceSiteHarvestProcess.coldProgress(job, 22_301);
        boolean sawWork = false;
        for (int step = 0; step < 256 && state.resourceSites().site(job.siteId()).harvestJob(job.id()).isPresent(); step++) {
            assertNotNull(due, "continuation must exist until the owner retires");
            var result = ResourceSiteHarvestProcessTest.stepCold(state, job.siteId(), due);
            state = result.state(); due = result.next();
            var active = state.resourceSites().site(job.siteId()).harvestJob(job.id());
            if (active.filter(value -> value.progress().completedCropSlots() > 0).isPresent()) {
                sawWork = true;
                assertEquals(1, active.orElseThrow().progress().completedCropSlots());
                assertTrue(active.orElseThrow().progress().complete(), "maintenance ends only this work offer");
            }
            var codec = new FrontierWorldStateCodec();
            state = codec.decode(codec.encode(state));
        }
        assertTrue(sawWork);
        assertTrue(state.resourceSites().site(job.siteId()).harvestJob(job.id()).isEmpty());
        var returned = state.inventory().items().get(tool.id());
        assertEquals(1, returned.condition().remaining());
        assertInstanceOf(InventoryCustody.ContainerSlot.class, returned.custody());
        assertTrue(state.resourceSites().cycle(job.siteId()).cellStates().values().stream().anyMatch(cell -> cell.growthStage() == 7));
    }
}
