package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalLogisticsProcessTest {
    @Test
    void terminalDeliveredGraphCompactsIntoOneBoundedPersistentReceipt() {
        FrontierWorldState settled = completedDelivery();
        RouteOperation operation = settled.operations().get(new SubjectId("operation:supply-1-2"));

        assertThrows(IllegalArgumentException.class, () -> initial().compactTerminalLogistics(operation.id(), 12L),
                "an active route and cargo may never be discarded as historical evidence");
        var planned = TerminalLogisticsProcess.plan(settled, TerminalLogisticsProcess.review(7, 1_000L));
        TerminalLogisticsCompacted compacted = assertInstanceOf(TerminalLogisticsCompacted.class, planned.getFirst().payload());
        assertEquals(operation.id(), compacted.operationId());
        assertInstanceOf(ScheduleEffect.Created.class, planned.get(1).payload());

        FrontierWorldState detached = settled.compactTerminalLogistics(compacted.operationId(), 1_000L);

        assertFalse(detached.operations().containsKey(operation.id()));
        assertFalse(detached.contracts().containsKey(new SubjectId("contract:supply-1-2")));
        TerminalLogisticsReceipt receipt = detached.logisticsHistory().receipts().get(operation.id());
        assertEquals(TerminalLogisticsReceipt.TerminalLogisticsOutcome.DELIVERED, receipt.outcome());
        assertEquals(1_000L, receipt.terminalAtTick());
        assertEquals(operation.participantIds(), receipt.participants());
        assertEquals(1L, detached.logisticsHistory().deliveredCount());
        assertEquals(detached, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(detached)));
        assertEquals(compacted, FrontierWorldRuntimeDefinition.payloadCodecs().decode(compacted.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(compacted)));
        assertThrows(IllegalArgumentException.class, () -> detached.compactTerminalLogistics(operation.id(), 1_100L));
    }

    @Test
    void receiptRetentionEvictsOnlyTheDeterministicallyOldestTerminalRecord() {
        LogisticsHistory history = LogisticsHistory.empty();
        for (int index = 0; index <= LogisticsHistory.MAX_RECEIPTS; index++) history = history.record(receipt(index, index == 1 ? 1L : index + 10L));

        assertEquals(LogisticsHistory.MAX_RECEIPTS, history.receipts().size());
        assertFalse(history.receipts().containsKey(new SubjectId("operation:receipt-1")));
        assertTrue(history.receipts().containsKey(new SubjectId("operation:receipt-" + LogisticsHistory.MAX_RECEIPTS)));
    }

    @Test
    void scheduledCompactionSurvivesSnapshotRecoveryWithoutRestoringTheActiveGraph() {
        WorldId world = new WorldId("frontier:terminal-logistics-recovery");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base =
                FrontierWorldRuntimeDefinition.developmentRouteSceneReturnConfiguration(world, 91L);
        FrontierWorldState settled = completedDelivery(world);
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration =
                new FrontierEngineConfiguration<>(world, settled, SimInstant.ZERO, base.commandPlanner(), base.scheduledPlanner(),
                        base.reducer(),
                        new FrontierWorldStateCodec(settled.bootstrap()), base.projectionMapper(), base.limits(), List.of(TerminalLogisticsProcess.review(1, 1L)),
                        base.transactionCommitter(), base.stateValidator());
        var engine = FrontierEngines.create(configuration);

        engine.advanceTo(new SimInstant(1L), new WorkBudget(64, 128));
        var beforeRecovery = engine.checkpoint();
        var recovered = FrontierEngines.recover(configuration, new RecoveryImage(world,
                java.util.Optional.of(new SnapshotRecord(beforeRecovery, beforeRecovery.revision().value())), List.of()));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(recovered.checkpoint().canonicalState());

        assertFalse(state.operations().containsKey(new SubjectId("operation:supply-1-2")));
        assertEquals(1L, state.logisticsHistory().deliveredCount());
        assertEquals(beforeRecovery, recovered.checkpoint());
    }

    private static FrontierWorldState completedDelivery() { return completedDelivery(new WorldId("frontier:terminal-logistics")); }
    private static FrontierWorldState completedDelivery(WorldId world) {
        FrontierWorldState state = initial(world); RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-2"));
        SupplyContract contract = state.contracts().get(new SubjectId("contract:supply-1-2"));
        SubjectId item = state.inventory().cargo().get(operation.cargoId()).itemIds().getFirst();
        SubjectId receiver = FrontierCargoValidation.receiverStore(state.bootstrap(), operation);
        ExactInventory inventory = state.inventory().completeCargoHandoff(operation.cargoId(), List.of(new CargoHandoffPlacement(item,
                new InventoryCustody.ContainerSlot(receiver, state.inventory().firstFreeSlot(receiver).orElseThrow()))));
        Map<SubjectId, SupplyContract> contracts = new LinkedHashMap<>(state.contracts()); contracts.put(contract.id(), contract.withStatus(ContractStatus.DELIVERED));
        RouteOperation completed = new RouteOperation(operation.id(), operation.settlementId(), operation.cargoId(), operation.destinationId(),
                operation.participantIds(), operation.route(), 0, OperationStage.COMPLETED);
        Map<SubjectId, RouteOperation> operations = new LinkedHashMap<>(state.operations()); operations.put(completed.id(), completed);
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), inventory,
                state.productionJobs(), contracts, operations, state.logisticsHistory(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(),
                state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(), state.routeTopology(),
                state.strategicPlans(), state.humanPopulation(), state.companies(), state.resourceSites());
    }

    private static TerminalLogisticsReceipt receipt(int index, long atTick) {
        SubjectId operation = new SubjectId("operation:receipt-" + index);
        return new TerminalLogisticsReceipt(operation, new SubjectId("contract:receipt-" + index), new SubjectId("cargo:receipt-" + index),
                new SubjectId("settlement:1"), new SubjectId("hive:frontier"), List.of(new SubjectId("resident:1-1")),
                TerminalLogisticsReceipt.TerminalLogisticsOutcome.DELIVERED, atTick);
    }

    private static FrontierWorldState initial() { return initial(new WorldId("frontier:terminal-logistics")); }
    private static FrontierWorldState initial(WorldId world) { return FrontierWorldRuntimeDefinition.developmentRouteSceneReturnConfiguration(world, 91L).initialState(); }
}
