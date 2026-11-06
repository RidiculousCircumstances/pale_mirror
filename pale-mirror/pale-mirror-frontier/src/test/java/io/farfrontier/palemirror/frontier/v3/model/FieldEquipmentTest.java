package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FieldEquipmentTest {
    @Test void acquisitionUsesExistingStockDespiteReceivingFilterButRejectsForeignOrMissingCustody() {
        var fixture = ResourceSiteHarvestProcessTest.awaitingEquipment(125L);
        var job = fixture.job(); var before = fixture.state();
        var item = before.inventory().items().get(job.equipment().itemId());
        var container = before.inventory().containers().get(job.equipment().source().containerId());
        var policy = container.storage().orElseThrow();
        var filtered = new ResourceStoragePolicy(policy.endpoint(), ResourceStorageFilter.all().excluding(Set.of(item.itemKind())),
                policy.priority(), policy.role());
        var inventory = before.inventory();
        var state = before.withInventory(new ExactInventory(ImmutableLookupMap.withEntry(inventory.containers(), container.id(),
                container.withStorage(filtered)), inventory.items(), inventory.cargo(), inventory.playerItems(), inventory.worldCarrierItems(),
                inventory.conflicts(), inventory.surfaces(), inventory.economics(), inventory.fungibleResources()));
        assertTrue(WorkEquipmentSelection.available(state, item.economicOwnerId(), item,
                FieldEquipment.requirements(ResourceSiteKind.WHEAT_FIELD), Set.of()));
        assertTrue(FieldEquipment.transferAvailable(state, job));
        assertFalse(WorkEquipmentStorage.available(state, item, policy.endpoint()));
        var other = state.bootstrap().settlements().stream().filter(home -> !home.id().equals(item.economicOwnerId())).findFirst().orElseThrow().id();
        var foreign = state.withInventory(state.inventory().transferItemTitle(item, other));
        assertFalse(WorkEquipmentStorage.sourceAvailable(foreign, foreign.inventory().items().get(item.id()), container.id()));
        var moved = state.withInventory(state.inventory().moveObservedItem(item.id(), item.custody(), new InventoryCustody.Player(new java.util.UUID(29, 4))));
        assertFalse(WorkEquipmentStorage.sourceAvailable(moved, moved.inventory().items().get(item.id()), container.id()));
        var conflict = state.withInventory(state.inventory().withSurfaceStatus(container.id(), ContainerSurfaceStatus.CONFLICT));
        assertFalse(WorkEquipmentStorage.sourceAvailable(conflict, item, container.id()));
    }

    @Test void ownerItemReadsFollowWearTitleRemovalAndIndependentInventoryBranches() {
        var fixture = ResourceSiteHarvestProcessTest.awaitingEquipment(125L);
        var inventory = fixture.state().inventory();
        var tool = inventory.items().get(fixture.job().equipment().itemId());
        var foreign = inventory.items().values().stream().filter(item -> item.condition().durable()
                && !item.economicOwnerId().equals(tool.economicOwnerId())).findFirst().orElseThrow();
        var owned = ExactItemReadIndex.owned(inventory, tool.economicOwnerId());
        var kind = ExactItemReadIndex.owned(inventory, tool.economicOwnerId(), tool.itemKind());
        var foreignWear = inventory.changeCondition(foreign, new ItemCondition(foreign.condition().maximumDamage(), 1));
        assertSame(owned, ExactItemReadIndex.owned(foreignWear, tool.economicOwnerId()),
                "foreign wear cannot rebuild unchanged owner rows");
        assertSame(kind, ExactItemReadIndex.owned(foreignWear, tool.economicOwnerId(), tool.itemKind()));
        var worn = foreignWear.changeCondition(tool, new ItemCondition(tool.condition().maximumDamage(), 1));
        assertEquals(tool.withCondition(new ItemCondition(tool.condition().maximumDamage(), 1)),
                ExactItemReadIndex.owned(worn, tool.economicOwnerId(), tool.itemKind()).stream()
                        .filter(item -> item.id().equals(tool.id())).findFirst().orElseThrow());
        var transferred = worn.transferItemTitle(worn.items().get(tool.id()), foreign.economicOwnerId());
        assertTrue(ExactItemReadIndex.owned(transferred, tool.economicOwnerId()).stream().noneMatch(item -> item.id().equals(tool.id())));
        assertTrue(ExactItemReadIndex.owned(transferred, foreign.economicOwnerId(), tool.itemKind()).stream()
                .anyMatch(item -> item.id().equals(tool.id())));
        var removed = transferred.withoutItem(tool.id());
        assertTrue(ExactItemReadIndex.owned(removed, foreign.economicOwnerId()).stream().noneMatch(item -> item.id().equals(tool.id())));
        assertEquals(owned, ExactItemReadIndex.owned(inventory, tool.economicOwnerId()), "old branch retains pristine title and condition");
    }

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
        var claims = WorkEquipmentClaimComposition.reserved(state);
        var prepared = state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(job.siteId())
                .withHarvestEquipment(job, job.equipment().prepare(step))));
        assertSame(claims, WorkEquipmentClaimComposition.reserved(prepared),
                "preparing the same owner's equipment step cannot manufacture a changed reservation image");
        assertFalse(ContainerPhysicalAuthorityComposition.pending(state, step.slot().containerId()));
        assertTrue(ContainerPhysicalAuthorityComposition.pending(prepared, step.slot().containerId()));
        assertFalse(ContainerPhysicalAuthorityComposition.pending(state, step.slot().containerId()), "an immutable predecessor cannot inherit a candidate fence");
        assertTrue(ContainerPhysicalAuthorityComposition.pending(prepared, step.slot().containerId()));
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
        assertFalse(ContainerPhysicalAuthorityComposition.pending(after, step.slot().containerId()));
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
                FieldEquipment.requirements(ResourceSiteKind.WHEAT_FIELD), java.util.Set.of()), "index membership grants no work availability");
        assertEquals(before.resourceSites().cycle(job.siteId()), after.resourceSites().cycle(job.siteId()));
        assertFalse(WorkEquipmentClaimComposition.reserved(after).contains(tool.id()));
    }
    @Test void acquisitionRetainsTheExactItemAndDuplicateTransferCannotEquipItTwice() {
        var fixture = ResourceSiteHarvestProcessTest.awaitingEquipment(125L);
        var job = fixture.job(); var before = fixture.state();
        assertEquals(ResourceSiteHarvestGoal.Kind.EQUIPMENT_SERVICE, ResourceSiteHarvestGoal.current(before, job).kind());
        var candidate = FrontierResourceSiteHarvestSceneSupport.candidate(before, job).orElseThrow();
        assertEquals(FrontierResourceSiteHarvestSceneSupport.demandPosition(before, job), candidate.cropSlot(),
                "tool acquisition follows the same worker-side demand at scene admission and continuation");
        assertEquals(before.actorLocations().get(job.workerId()).body().supportingSurface().support().offset(0, 1, 0),
                candidate.cropSlot(), "an unfinished field cannot redirect a service journey's demand back to its crop");
        var event = new FieldEquipmentChanged(job.siteId(), job.id(), WorkToolAssignment.Stage.ACQUIRING,
                FieldEquipmentChanged.Action.COLD_TRANSFER, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> FieldEquipmentProcess.reduce(before, job.siteId(), event, 22_100));
        var atSource = before.withActorBody(job.workerId(), job.equipment().source().station().standingBody());
        var after = FieldEquipmentProcess.reduce(atSource, job.siteId(), event, 22_100);
        var held = after.inventory().items().get(job.equipment().itemId());
        assertEquals(new InventoryCustody.Actor(job.workerId()), held.custody());
        assertEquals(before.inventory().items().get(held.id()).condition(), held.condition());
        assertEquals(WorkToolAssignment.Stage.EQUIPPED, after.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow().equipment().stage());
        var productive = EquipmentProductiveCapacity.items(after, held.economicOwnerId(), Set.of(job.workerId()), FieldEquipment.requirements(ResourceSiteKind.WHEAT_FIELD));
        assertTrue(productive.stream().anyMatch(tool -> tool.id().equals(held.id())));
        var demand = new FieldEquipmentDemand(); var needs = demand.read(after, held.economicOwnerId());
        assertFalse(needs.isEmpty());
        var other = after.humanPopulation().residents().keySet().stream().filter(id -> !id.equals(job.workerId())).findFirst().orElseThrow();
        var moved = after.withActorBody(other, after.actorLocations().get(other).body().offset(1, 0, 0));
        assertSame(productive, EquipmentProductiveCapacity.items(moved, held.economicOwnerId(), Set.of(job.workerId()), FieldEquipment.requirements(ResourceSiteKind.WHEAT_FIELD)),
                "another person's walk cannot change actual usable equipment");
        assertSame(needs, demand.read(moved, held.economicOwnerId()),
                "unchanged local crops, workers, equipment and storage cannot gain a new wear forecast from foreign movement");
        var retained = EquipmentStockProjection.retainedItems(after, held.economicOwnerId());
        var foreign = after.inventory().items().values().stream().filter(item -> item.condition().durable()
                && !item.economicOwnerId().equals(held.economicOwnerId())).findFirst().orElseThrow();
        var changed = after.withInventory(after.inventory().changeCondition(foreign,
                new ItemCondition(foreign.condition().maximumDamage(), 1)));
        assertSame(productive, EquipmentProductiveCapacity.items(changed, held.economicOwnerId(), Set.of(job.workerId()), FieldEquipment.requirements(ResourceSiteKind.WHEAT_FIELD)));
        assertSame(needs, demand.read(changed, held.economicOwnerId()));
        assertSame(retained, EquipmentStockProjection.retainedItems(changed, held.economicOwnerId()),
                "foreign tool wear cannot reconstruct the own retained productive fleet");
        assertThrows(IllegalArgumentException.class, () -> FieldEquipmentProcess.reduce(after, job.siteId(), event, 22_100));
        var codec = new FrontierWorldStateCodec();
        assertEquals(after, codec.decode(codec.encode(after)));
    }

    @Test void changedTargetRequirementReturnsWornToolWithoutWorkingOrLosingResourcesAcrossRecovery() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var job = fixture.job(); var state = fixture.state();
        var event = new FieldEquipmentChanged(job.siteId(), job.id(), job.equipment().stage(),
                FieldEquipmentChanged.Action.SERVICE_REQUIRED, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> FieldEquipmentProcess.reduce(fixture.state(), job.siteId(), event, 22_301),
                "a healthy tool cannot falsely end a work offer");
        var tool = state.inventory().items().get(job.equipment().itemId());
        state = state.withInventory(state.inventory().changeCondition(tool,
                new ItemCondition(tool.condition().maximumDamage(), tool.condition().maximumDamage() - 1)));
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertTrue(FieldEquipment.needsService(state, job));
        var cells = state.resourceSites().cycle(job.siteId()); var stock = state.inventory();
        var due = ResourceSiteHarvestProcess.coldProgress(job, 22_301);
        var plan = ResourceSiteHarvestProcess.planColdProgress(state, due, 22_301);
        assertTrue(plan.stream().anyMatch(e -> e.payload().equals(event)), "recovery's ordinary preview must request service, not start unsupported labour");
        var after = FieldEquipmentProcess.reduce(state, job.siteId(), event, 22_301);
        assertEquals(cells, after.resourceSites().cycle(job.siteId())); assertEquals(stock, after.inventory());
        var ended = after.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow();
        assertTrue(ended.progress().complete()); assertEquals(job.harvestedYieldQuantity(), ended.harvestedYieldQuantity());
        assertEquals(job.undeliveredYieldQuantity(), ended.undeliveredYieldQuantity());
        var payloads = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(event, payloads.decode(event.type(), payloads.encode(event)));
        after = codec.decode(codec.encode(after));
        for (int step = 0; step < 256 && after.resourceSites().site(job.siteId()).harvestJob(job.id()).isPresent(); step++) {
            var result = ResourceSiteHarvestProcessTest.stepCold(after, job.siteId(), due);
            after = result.state(); due = result.next();
        }
        assertTrue(after.resourceSites().site(job.siteId()).harvestJob(job.id()).isEmpty());
        assertEquals(cells, after.resourceSites().cycle(job.siteId()), "service cannot consume an unworked cell");
        var returned = after.inventory().items().get(tool.id());
        assertInstanceOf(InventoryCustody.ContainerSlot.class, returned.custody());
        assertEquals(1, returned.condition().remaining());
    }

    @Test void finalUsefulOperationWearsOnceReturnsToolAndLeavesOtherCellsAvailable() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var job = fixture.job(); var state = fixture.state();
        var tool = state.inventory().items().get(job.equipment().itemId());
        state = state.withInventory(state.inventory().changeCondition(tool,
                new ItemCondition(tool.condition().maximumDamage(), tool.condition().maximumDamage() - 2)));
        var due = ResourceSiteHarvestProcess.coldProgress(job, 22_301);
        boolean sawWork = false;
        for (int step = 0; step < 2048 && state.resourceSites().site(job.siteId()).harvestJob(job.id()).isPresent(); step++) {
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
