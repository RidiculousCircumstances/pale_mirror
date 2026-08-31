package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveNutrientTransferProcessTest {
    @Test
    void coldTransferMovesTheSameExactNutrientAcrossNamedStoresAndRetainsItsReceipt() {
        FrontierWorldState baseline = growthTaskState();
        StrategicTask task = baseline.strategicPlans().tasks().get(new SubjectId("task:hive-nutrient"));
        ExactItemStack biomass = baseline.inventory().items().get(new SubjectId("item:bootstrap-hive-biomass"));
        HiveNutrientTransfer transfer = HiveNutrientTransferProcess.create(baseline, task, biomass, new SubjectId("container:hive-west-store"), 0);

        assertEquals(new HiveNutrientTransferStarted(transfer), FrontierWorldRuntimeDefinition.payloadCodecs().decode(
                "frontier.hive_nutrient_transfer_started", FrontierWorldRuntimeDefinition.payloadCodecs().encode(new HiveNutrientTransferStarted(transfer))));

        FrontierWorldState started = HiveNutrientTransferProcess.reduceStarted(baseline, baseline.bootstrap().hive().id(), transfer);
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
    void materializedEndpointBlocksTheSameCargoInsteadOfWritingItToTheOtherNest() {
        FrontierWorldState baseline = growthTaskState();
        StrategicTask task = baseline.strategicPlans().tasks().get(new SubjectId("task:hive-nutrient"));
        ExactItemStack biomass = baseline.inventory().items().get(new SubjectId("item:bootstrap-hive-biomass"));
        HiveNutrientTransfer transfer = HiveNutrientTransferProcess.create(baseline, task, biomass, new SubjectId("container:hive-west-store"), 0);
        FrontierWorldState started = HiveNutrientTransferProcess.reduceStarted(baseline, transfer.hiveId(), transfer);
        FrontierWorldState endpointMaterialized = started.withInventory(started.inventory().withSurfaceStatus(transfer.targetStoreId(), ContainerSurfaceStatus.PREPARED));

        ScheduledAction action = HiveNutrientTransferProcess.advance(transfer, 20L);
        HiveNutrientTransferBlocked blocked = (HiveNutrientTransferBlocked) HiveNutrientTransferProcess.plan(endpointMaterialized, action).getFirst().payload();
        FrontierWorldState terminal = HiveNutrientTransferProcess.reduceBlocked(endpointMaterialized, transfer.hiveId(), blocked);
        assertEquals(HiveNutrientTransferBlockReason.ENDPOINT_MATERIALIZED, terminal.hiveColony().nutrientTransfers().get(transfer.id()).blockReason().orElseThrow());
        assertEquals(new InventoryCustody.Cargo(transfer.cargoId()), terminal.inventory().items().get(biomass.id()).custody());
        assertTrue(terminal.inventory().itemAt(transfer.targetStoreId(), transfer.targetSlot().slot()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> HiveNutrientTransferProcess.reduceCompleted(terminal, transfer.hiveId(),
                new HiveNutrientTransferCompleted(new HiveNutrientReceipt(transfer.id(), transfer.hiveId(), transfer.cargoId(), transfer.itemId(), transfer.sourceSlot(), transfer.targetSlot()))));
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
