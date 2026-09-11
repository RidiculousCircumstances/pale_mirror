package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveNutrientTransferProcessTest {
    @Test
    void coldFungibleNutrientCargoMovesOneLotAcrossNestsWithoutStackIdentity() {
        FrontierWorldState baseline = growthTaskState();
        SubjectId hive = baseline.bootstrap().hive().id(), east = new SubjectId("container:hive-east-store"), west = new SubjectId("container:hive-west-store");
        SubjectId lotId = new SubjectId("lot:hive-east-biomass"), accountId = new SubjectId("custody:hive-east-biomass");
        ResourceLot biomass = new ResourceLot(lotId, hive, "minecraft:rotten_flesh", 64, "bootstrap", List.of());
        CustodyAccount account = new CustodyAccount(accountId, new ResourceCustody.Container(east), Map.of(lotId, 64), Map.of());
        FrontierWorldState pending = baseline.withInventory(baseline.inventory().withoutItem(new SubjectId("item:bootstrap-hive-biomass"))
                .withFungibleResources(FungibleResourceLedger.empty().issue(biomass, account)));
        StrategicTask task = pending.strategicPlans().tasks().get(new SubjectId("task:hive-nutrient"));
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> departure = HiveGrowthProcess.planStart(pending, HiveGrowthProcess.start(task, 100L));
        HiveNutrientTransfer transfer = ((HiveNutrientTransferStarted) departure.getFirst().payload()).transfer();

        assertTrue(transfer.fungibleContents());
        FrontierWorldState inTransit = HiveNutrientTransferProcess.reduceStarted(pending, hive, transfer);
        assertEquals(64, inTransit.inventory().fungibleResources().totalQuantity(hive, "minecraft:rotten_flesh"));
        assertTrue(inTransit.inventory().cargo().get(transfer.cargoId()).fungibleContents());
        for (int cursor = 1; cursor < transfer.corridor().size(); cursor++) {
            inTransit = HiveNutrientTransferProcess.reduceAdvanced(inTransit, hive, new HiveNutrientTransferAdvanced(transfer.id(), cursor));
        }
        HiveNutrientReceipt receipt = new HiveNutrientReceipt(transfer.id(), hive, transfer.cargoId(), transfer.itemId(), transfer.sourceSlot(), transfer.targetSlot());
        FrontierWorldState delivered = HiveNutrientTransferProcess.reduceCompleted(inTransit, hive, new HiveNutrientTransferCompleted(receipt));

        assertTrue(delivered.inventory().cargo().isEmpty());
        assertEquals(64, delivered.inventory().fungibleResources().totalQuantity(hive, "minecraft:rotten_flesh"));
        assertEquals(west, ((ResourceCustody.Container) delivered.inventory().fungibleResources().accounts().values().stream()
                .filter(value -> value.lotQuantities().containsKey(lotId)).findFirst().orElseThrow().custody()).containerId());
        assertEquals(delivered, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(delivered)));
    }

    @Test
    void hotFungibleSourceRemainsBoundUntilObservedRemovalCreatesOneColdCargoPortion() {
        FrontierWorldState baseline = growthTaskState();
        SubjectId hive = baseline.bootstrap().hive().id(), east = new SubjectId("container:hive-east-store");
        SubjectId lotId = new SubjectId("lot:hive-east-biomass"), accountId = new SubjectId("custody:hive-east-biomass");
        ResourceLot biomass = new ResourceLot(lotId, hive, "minecraft:rotten_flesh", 64, "bootstrap", List.of());
        CustodyAccount account = new CustodyAccount(accountId, new ResourceCustody.Container(east), Map.of(lotId, 64), Map.of());
        FungibleResourceLedger cold = FungibleResourceLedger.empty().issue(biomass, account);
        FungiblePhysicalObservation.Stack sourceStack = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(east, 0)), biomass.itemKind(), 64);
        FungibleResourceLedger hot = cold.rebind(accountId, 7L, FungiblePhysicalObservation.bind(cold, accountId, 7L, List.of(sourceStack)));
        FrontierWorldState sourceHeld = ReferenceContainerCustodyFixtures.observedAndHeld(baseline.withInventory(baseline.inventory()
                .withFungibleResources(hot)), east);
        StrategicTask task = sourceHeld.strategicPlans().tasks().get(new SubjectId("task:hive-nutrient"));

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = HiveGrowthProcess.planStart(sourceHeld,
                HiveGrowthProcess.start(task, 100L));
        HiveNutrientTransfer transfer = ((HiveNutrientTransferStarted) planned.getFirst().payload()).transfer();
        assertEquals(HiveNutrientTransferPhase.DEPARTURE_PENDING, transfer.phase());
        FrontierWorldState pending = HiveNutrientTransferProcess.reduceStarted(sourceHeld, hive, transfer);
        assertFalse(pending.inventory().cargo().containsKey(transfer.cargoId()));
        assertEquals(7L, pending.inventory().fungibleResources().bindings().values().iterator().next().authorityEpoch());

        var departure = HiveNutrientTransferStateSupport.departureIntent(pending, transfer);
        FrontierWorldState running = pending.preparePhysicalIntent(departure).transitionPhysicalIntent(departure.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        FungibleNutrientDepartureObservation observed = new FungibleNutrientDepartureObservation(
                new PhysicalObservationId("observation:fungible-hive-nutrient-departure"), departure.id(), transfer.id(), transfer.cargoId(),
                accountId, lotId, 64, 7L, List.of());
        FrontierWorldState departed = running.transitionPhysicalIntent(departure.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observed));

        assertEquals(HiveNutrientTransferPhase.IN_TRANSIT, departed.hiveColony().nutrientTransfers().get(transfer.id()).phase());
        assertTrue(departed.inventory().cargo().get(transfer.cargoId()).fungibleContents());
        assertTrue(departed.inventory().fungibleResources().bindings().isEmpty());
        assertTrue(departed.inventory().fungibleResources().accounts().values().stream()
                .anyMatch(value -> value.custody() instanceof ResourceCustody.Cargo cargo && cargo.cargoId().equals(transfer.cargoId())));
        assertEquals(departed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(departed)));
    }

    @Test
    void coldFungibleBiomassIsClaimedAndConsumedWithoutAStableStackIdentity() {
        FrontierWorldState baseline = growthTaskState();
        SubjectId hive = baseline.bootstrap().hive().id(), west = new SubjectId("container:hive-west-store");
        SubjectId lotId = new SubjectId("lot:hive-west-biomass"), accountId = new SubjectId("custody:hive-west-biomass");
        ResourceLot biomass = new ResourceLot(lotId, hive, "minecraft:rotten_flesh", 64, "bootstrap", List.of());
        CustodyAccount account = new CustodyAccount(accountId, new ResourceCustody.Container(west), Map.of(lotId, 64), Map.of());
        ExactInventory inventory = baseline.inventory().withoutItem(new SubjectId("item:bootstrap-hive-biomass"))
                .withFungibleResources(FungibleResourceLedger.empty().issue(biomass, account));
        FrontierWorldState pending = baseline.withInventory(inventory);
        StrategicTask task = pending.strategicPlans().tasks().get(new SubjectId("task:hive-nutrient"));

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = HiveGrowthProcess.planStart(pending, HiveGrowthProcess.start(task, 100L));
        HiveGrowthJob job = ((HiveGrowthStarted) events.stream().filter(event -> event.payload() instanceof HiveGrowthStarted).findFirst().orElseThrow().payload()).job();
        HiveGrowthInputHold.FungibleCold hold = assertInstanceOf(HiveGrowthInputHold.FungibleCold.class, job.inputHold());
        FrontierWorldState active = pending.withStrategicPlans(pending.strategicPlans().transitionTask(task.id(), StrategicTaskStatus.ACTIVE));
        FrontierWorldState started = HiveGrowthProcess.reduceStarted(active, hive, new HiveGrowthStarted(job));
        FrontierWorldState consumed = HiveGrowthProcess.reduceConsumed(started, hive, new HiveGrowthBiomassConsumed(job.id(), job.consumedItemId()));

        assertEquals(64, started.inventory().fungibleResources().claims().get(hold.claimId()).quantity());
        assertFalse(consumed.inventory().fungibleResources().lots().containsKey(lotId));
        assertFalse(consumed.inventory().fungibleResources().claims().containsKey(hold.claimId()));
        assertEquals(job, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(started)).hiveColony().growthJobs().get(job.id()));
    }

    @Test
    void coldTransferMovesTheSameExactNutrientAcrossNamedStoresAndRetainsItsReceipt() {
        FrontierWorldState baseline = growthTaskState();
        StrategicTask task = baseline.strategicPlans().tasks().get(new SubjectId("task:hive-nutrient"));
        ExactItemStack biomass = baseline.inventory().items().get(new SubjectId("item:bootstrap-hive-biomass"));
        HiveNutrientTransfer transfer = HiveNutrientTransferProcess.create(baseline, task, biomass, new SubjectId("container:hive-west-store"), 0);

        assertEquals(new HiveNutrientTransferStarted(transfer), FrontierWorldRuntimeDefinition.payloadCodecs().decode(
                "frontier.hive_nutrient_transfer_started", FrontierWorldRuntimeDefinition.payloadCodecs().encode(new HiveNutrientTransferStarted(transfer))));

        FrontierWorldState started = HiveNutrientTransferProcess.reduceStarted(baseline, baseline.bootstrap().hive().id(), transfer);
        assertSame(baseline.hiveColony().addedOrgans(), started.hiveColony().addedOrgans());
        assertSame(baseline.hiveColony().bioformLifecycles(), started.hiveColony().bioformLifecycles());
        assertSame(baseline.hiveColony().mobilizations(), started.hiveColony().mobilizations());
        assertSame(baseline.hiveColony().nutrientReceipts(), started.hiveColony().nutrientReceipts());
        assertTrue(baseline.hiveColony().nutrientTransfers() != started.hiveColony().nutrientTransfers(),
                "only the nutrient-transfer contributor changes at this canonical owner boundary");
        assertEquals(new InventoryCustody.Cargo(transfer.cargoId()), started.inventory().items().get(biomass.id()).custody());
        assertTrue(started.inventory().itemAt(transfer.sourceStoreId(), transfer.sourceSlot().slot()).isEmpty(), "departure removes the source claim before transit");
        FrontierWorldState progressed = started;
        for (int cursor = 1; cursor < transfer.corridor().size(); cursor++) {
            progressed = HiveNutrientTransferProcess.reduceAdvanced(progressed, transfer.hiveId(), new HiveNutrientTransferAdvanced(transfer.id(), cursor));
        }
        HiveNutrientReceipt receipt = new HiveNutrientReceipt(transfer.id(), transfer.hiveId(), transfer.cargoId(), transfer.itemId(), transfer.sourceSlot(), transfer.targetSlot());
        FrontierWorldState completed = HiveNutrientTransferProcess.reduceCompleted(progressed, transfer.hiveId(), new HiveNutrientTransferCompleted(receipt));

        ExactItemStack arrived = completed.inventory().items().get(biomass.id());
        assertEquals(biomass.id(), arrived.id());
        assertEquals(transfer.targetSlot(), arrived.custody());
        assertTrue(!completed.inventory().cargo().containsKey(transfer.cargoId()));
        assertEquals(receipt, completed.hiveColony().nutrientReceipts().get(transfer.id()));
        assertEquals(new HiveNutrientTransferCompleted(receipt), FrontierWorldRuntimeDefinition.payloadCodecs().decode(
                "frontier.hive_nutrient_transfer_completed", FrontierWorldRuntimeDefinition.payloadCodecs().encode(new HiveNutrientTransferCompleted(receipt))));
        assertEquals(completed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(completed)));
    }

    @Test
    void materializedEndpointRetainsTheSameCargoUntilPhysicalArrivalIsObserved() {
        FrontierWorldState baseline = growthTaskState();
        StrategicTask task = baseline.strategicPlans().tasks().get(new SubjectId("task:hive-nutrient"));
        ExactItemStack biomass = baseline.inventory().items().get(new SubjectId("item:bootstrap-hive-biomass"));
        HiveNutrientTransfer transfer = HiveNutrientTransferProcess.create(baseline, task, biomass, new SubjectId("container:hive-west-store"), 0);
        FrontierWorldState started = HiveNutrientTransferProcess.reduceStarted(baseline, transfer.hiveId(), transfer);
        FrontierWorldState endpointMaterialized = ReferenceContainerCustodyFixtures.observedAndHeld(
                started.withInventory(started.inventory().withSurfaceStatus(transfer.targetStoreId(), ContainerSurfaceStatus.PREPARED)), transfer.targetStoreId());

        FrontierWorldState beforeArrival = endpointMaterialized;
        for (int cursor = 1; cursor < transfer.corridor().size() - 1; cursor++) {
            beforeArrival = HiveNutrientTransferProcess.reduceAdvanced(beforeArrival, transfer.hiveId(), new HiveNutrientTransferAdvanced(transfer.id(), cursor));
        }
        ScheduledAction action = HiveNutrientTransferProcess.advance(transfer, 20L);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = HiveNutrientTransferProcess.plan(beforeArrival, action);
        assertTrue(planned.get(1).payload() instanceof HiveNutrientTransferEndpointPrepared);
        HiveNutrientTransferAdvanced advanced = (HiveNutrientTransferAdvanced) planned.getFirst().payload();
        FrontierWorldState atTarget = HiveNutrientTransferProcess.reduceAdvanced(beforeArrival, transfer.hiveId(), advanced);
        HiveNutrientTransfer waiting = ((HiveNutrientTransferEndpointPrepared) planned.get(1).payload()).transfer();
        FrontierWorldState pending = HiveNutrientTransferProcess.reduceEndpointPrepared(atTarget, transfer.hiveId(), new HiveNutrientTransferEndpointPrepared(waiting));
        assertEquals(new InventoryCustody.Cargo(transfer.cargoId()), pending.inventory().items().get(biomass.id()).custody());
        assertTrue(pending.inventory().itemAt(transfer.targetStoreId(), transfer.targetSlot().slot()).isEmpty());
        var arrival = HiveNutrientTransferStateSupport.arrivalIntent(pending, waiting);
        FrontierWorldState prepared = pending.preparePhysicalIntent(arrival);
        FrontierWorldState running = prepared.transitionPhysicalIntent(arrival.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        HiveNutrientArrivalObservation observed = new HiveNutrientArrivalObservation(new PhysicalObservationId("observation:hive-nutrient-arrival"), arrival.id(),
                transfer.id(), transfer.cargoId(), transfer.itemId(), biomass.count());
        FrontierWorldState terminal = running.transitionPhysicalIntent(arrival.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observed));
        assertEquals(transfer.targetSlot(), terminal.inventory().items().get(biomass.id()).custody());
        assertEquals(HiveNutrientReceiptStatus.STORED, terminal.hiveColony().nutrientReceipts().get(transfer.id()).status());
    }

    @Test
    void activeSourceRequiresOneObservedDepartureBeforeTheSameItemBecomesColdCargo() {
        FrontierWorldState baseline = growthTaskState();
        StrategicTask task = baseline.strategicPlans().tasks().get(new SubjectId("task:hive-nutrient"));
        ExactItemStack biomass = baseline.inventory().items().get(new SubjectId("item:bootstrap-hive-biomass"));
        FrontierWorldState sourcePrepared = ReferenceContainerCustodyFixtures.observedAndHeld(
                baseline.withInventory(baseline.inventory().withSurfaceStatus(new SubjectId("container:hive-east-store"), ContainerSurfaceStatus.PREPARED)),
                new SubjectId("container:hive-east-store"));
        HiveNutrientTransfer transfer = HiveNutrientTransferProcess.create(sourcePrepared, task, biomass, new SubjectId("container:hive-west-store"), 0);
        assertEquals(HiveNutrientTransferPhase.DEPARTURE_PENDING, transfer.phase());
        FrontierWorldState pending = HiveNutrientTransferProcess.reduceStarted(sourcePrepared, transfer.hiveId(), transfer);
        assertEquals(transfer.sourceSlot(), pending.inventory().items().get(transfer.itemId()).custody());
        assertTrue(!pending.inventory().cargo().containsKey(transfer.cargoId()));
        var departure = HiveNutrientTransferStateSupport.departureIntent(pending, transfer);
        FrontierWorldState running = pending.preparePhysicalIntent(departure).transitionPhysicalIntent(departure.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        HiveNutrientDepartureObservation observed = new HiveNutrientDepartureObservation(new PhysicalObservationId("observation:hive-nutrient-departure"), departure.id(),
                transfer.id(), transfer.cargoId(), transfer.itemId(), biomass.count());
        FrontierWorldState departed = running.transitionPhysicalIntent(departure.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observed));
        assertEquals(HiveNutrientTransferPhase.IN_TRANSIT, departed.hiveColony().nutrientTransfers().get(transfer.id()).phase());
        assertEquals(new InventoryCustody.Cargo(transfer.cargoId()), departed.inventory().items().get(transfer.itemId()).custody());
        assertEquals(departed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(departed)));
    }

    @Test
    void westGrowthWaitsForItsOwnInboundReceiptRatherThanConsumingEastBiomass() {
        FrontierWorldState baseline = growthTaskState();
        StrategicTask task = baseline.strategicPlans().tasks().get(new SubjectId("task:hive-nutrient"));
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> departure = HiveGrowthProcess.planStart(baseline, HiveGrowthProcess.start(task, 100L));
        HiveNutrientTransfer transfer = ((HiveNutrientTransferStarted) departure.getFirst().payload()).transfer();
        assertEquals(new SubjectId("container:hive-west-store"), transfer.targetStoreId());
        FrontierWorldState progressed = HiveNutrientTransferProcess.reduceStarted(baseline, transfer.hiveId(), transfer);
        for (int cursor = 1; cursor < transfer.corridor().size(); cursor++) {
            progressed = HiveNutrientTransferProcess.reduceAdvanced(progressed, transfer.hiveId(), new HiveNutrientTransferAdvanced(transfer.id(), cursor));
        }
        HiveNutrientReceipt receipt = new HiveNutrientReceipt(transfer.id(), transfer.hiveId(), transfer.cargoId(), transfer.itemId(), transfer.sourceSlot(), transfer.targetSlot());
        FrontierWorldState arrived = HiveNutrientTransferProcess.reduceCompleted(progressed, transfer.hiveId(), new HiveNutrientTransferCompleted(receipt));
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> growth = HiveGrowthProcess.planStart(arrived, HiveGrowthProcess.start(task, 200L));
        HiveGrowthJob job = ((HiveGrowthStarted) growth.stream().filter(event -> event.payload() instanceof HiveGrowthStarted).findFirst().orElseThrow().payload()).job();
        assertEquals(new SubjectId("nest:seed-west"), job.nestId());
        assertEquals(transfer.itemId(), job.consumedItemId());
        FrontierWorldState activeGrowth = arrived.withStrategicPlans(arrived.strategicPlans().transitionTask(task.id(), StrategicTaskStatus.ACTIVE));
        FrontierWorldState startedGrowth = HiveGrowthProcess.reduceStarted(activeGrowth, job.hiveId(), new HiveGrowthStarted(job));
        FrontierWorldState consumedGrowth = HiveGrowthProcess.reduceConsumed(startedGrowth, job.hiveId(), new HiveGrowthBiomassConsumed(job.id(), job.consumedItemId()));
        HiveNutrientReceipt consumedReceipt = consumedGrowth.hiveColony().nutrientReceipts().get(transfer.id());
        assertEquals(HiveNutrientReceiptStatus.CONSUMED, consumedReceipt.status());
        assertEquals(job.id(), consumedReceipt.consumedByJobId().orElseThrow());
        assertEquals(consumedGrowth, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(consumedGrowth)));
    }

    @Test
    void conflictedWestStoreBlocksOnlyItsGrowthAndInboundTransfer() {
        FrontierWorldState baseline = growthTaskState();
        StrategicTask task = baseline.strategicPlans().tasks().get(new SubjectId("task:hive-nutrient"));
        SubjectId west = new SubjectId("container:hive-west-store"); SubjectId east = new SubjectId("container:hive-east-store");
        FrontierWorldState conflicted = withReplicaConflict(baseline, west);

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> growth = HiveGrowthProcess.planStart(conflicted, HiveGrowthProcess.start(task, 100L));
        StrategicTaskTransition blockedGrowth = (StrategicTaskTransition) growth.getFirst().payload();
        assertEquals(StrategicTaskStatus.BLOCKED, blockedGrowth.status(),
                "the exact conflicted target store cannot borrow remote biomass or its own capacity");
        assertTrue(!ReferenceContainerCustody.blocksCanonicalUse(conflicted, east),
                "one nest store conflict must not fence the independent east source store");

        ExactItemStack biomass = baseline.inventory().items().get(new SubjectId("item:bootstrap-hive-biomass"));
        HiveNutrientTransfer transfer = HiveNutrientTransferProcess.create(baseline, task, biomass, west, 0);
        FrontierWorldState inTransit = HiveNutrientTransferProcess.reduceStarted(baseline, transfer.hiveId(), transfer);
        FrontierWorldState targetConflict = withReplicaConflict(inTransit, west);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> advance = HiveNutrientTransferProcess.plan(targetConflict,
                HiveNutrientTransferProcess.advance(transfer, 120L));
        assertTrue(advance.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                        .anyMatch(HiveNutrientTransferBlocked.class::isInstance),
                "an already admitted transfer visibly stops at its exact conflicted endpoint");
        assertTrue(!ReferenceContainerCustody.blocksCanonicalUse(targetConflict, east),
                "the endpoint fence remains local while another store stays eligible");
    }

    private static FrontierWorldState withReplicaConflict(FrontierWorldState state, SubjectId store) {
        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(store, ReferenceContainerCustody.semanticKind(state, store), 0L,
                ReferenceContainerCustody.canonicalFingerprint(state, store), ReferenceContainerCustody.provenance(store));
        return state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(PhysicalReplicaCustodyState.empty().declare(expected)
                .observe(store, 0L, 1L, "sha256:player-changed", "foreign:player", 0L)));
    }

    private static FrontierWorldState growthTaskState() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-nutrient"), 93L));
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:hive-nutrient"), hive,
                StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:hive-nutrient"), objective.id(), hive, StrategicTaskKind.GROW_HIVE_ORGANISM,
                Optional.empty(), List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS), List.of(), StrategicTaskStatus.PENDING);
        return state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
    }
}
