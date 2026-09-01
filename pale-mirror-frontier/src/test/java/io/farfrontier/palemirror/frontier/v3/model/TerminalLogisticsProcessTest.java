package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
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
                FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 91L);
        FrontierWorldState settled = completedDeliveryWithConfirmedHandoff(world);
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
        assertFalse(state.physicalIntents().containsKey(new PhysicalIntentId("intent:cargo-handoff-supply-1-2")));
        assertFalse(state.physicalObservations().containsKey(new PhysicalObservationId("observation:terminal-logistics-confirmed-handoff")));
        assertEquals(1L, state.logisticsHistory().deliveredCount());
        assertEquals(beforeRecovery, recovered.checkpoint());
    }

    @Test
    void terminalCompactionRetiresOnlyTheConfirmedPhysicalReceiptsOwnedByThatDelivery() {
        FrontierWorldState settled = completedDeliveryWithConfirmedHandoff();
        PhysicalIntentId intentId = new PhysicalIntentId("intent:cargo-handoff-supply-1-2");
        PhysicalObservationId observationId = new PhysicalObservationId("observation:terminal-logistics-confirmed-handoff");
        PhysicalIntentId loadingIntentId = new PhysicalIntentId("intent:cargo-loading-supply-1-2");
        PhysicalObservationId loadingObservationId = new PhysicalObservationId("observation:terminal-logistics-confirmed-loading");

        FrontierWorldState detached = settled.compactTerminalLogistics(new SubjectId("operation:supply-1-2"), 1_000L);

        assertFalse(detached.physicalIntents().containsKey(intentId));
        assertFalse(detached.physicalObservations().containsKey(observationId));
        assertFalse(detached.physicalIntents().containsKey(loadingIntentId));
        assertFalse(detached.physicalObservations().containsKey(loadingObservationId));
        assertEquals(detached, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(detached)));
    }

    private static FrontierWorldState completedDelivery() { return completedDelivery(new WorldId("frontier:terminal-logistics")); }

    private static FrontierWorldState completedDeliveryWithConfirmedHandoff() {
        return completedDeliveryWithConfirmedHandoff(new WorldId("frontier:terminal-logistics"));
    }

    private static FrontierWorldState completedDeliveryWithConfirmedHandoff(WorldId world) {
        FrontierWorldState delivered = completedDelivery(world);
        SubjectId operationId = new SubjectId("operation:supply-1-2");
        SubjectId cargoId = new SubjectId("cargo:supply-1-2");
        SubjectId itemId = new SubjectId("item:production-1-1-bread");
        PhysicalIntentId intentId = new PhysicalIntentId("intent:cargo-handoff-supply-1-2");
        PhysicalObservationId observationId = new PhysicalObservationId("observation:terminal-logistics-confirmed-handoff");
        CargoHandoffObservation observation = new CargoHandoffObservation(observationId, intentId, cargoId, List.of(
                new CargoHandoffPlacement(itemId, new InventoryCustody.ContainerSlot(new SubjectId("container:hive-west-store"), 0))));
        PhysicalIntent intent = new PhysicalIntent(intentId, PhysicalIntentKind.CARGO_HANDOFF, PhysicalIntentStatus.PREPARED,
                operationId, List.of(operationId, cargoId), new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO),
                0, PhysicalPostcondition.CARGO_HANDOFF_OBSERVED).withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(observationId));
        Map<PhysicalIntentId, PhysicalIntent> intents = new LinkedHashMap<>(delivered.physicalIntents()); intents.put(intentId, intent);
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(delivered.physicalObservations()); observations.put(observationId, observation);
        PhysicalIntentId loadingIntentId = new PhysicalIntentId("intent:cargo-loading-supply-1-2");
        PhysicalObservationId loadingObservationId = new PhysicalObservationId("observation:terminal-logistics-confirmed-loading");
        CargoLoadObservation loading = new CargoLoadObservation(loadingObservationId, loadingIntentId, new SubjectId("contract:supply-1-2"), cargoId, itemId, 1);
        intents.put(loadingIntentId, new PhysicalIntent(loadingIntentId, PhysicalIntentKind.CARGO_LOADING, PhysicalIntentStatus.PREPARED,
                new SubjectId("contract:supply-1-2"), List.of(new SubjectId("contract:supply-1-2"), cargoId, itemId),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.CARGO_LOADED_FROM_DEPOT_OBSERVED)
                .withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(loadingObservationId)));
        observations.put(loadingObservationId, loading);
        return new FrontierWorldState(delivered.bootstrap(), delivered.actorLocations(), delivered.structureConditions(), delivered.infection(), delivered.inventory(),
                delivered.productionJobs(), delivered.contracts(), delivered.operations(), delivered.logisticsHistory(), intents, observations, delivered.sceneLeases(),
                delivered.hiveColony(), delivered.structureDamage(), delivered.physicalDeltas(), delivered.ambientLeases(), delivered.routeConstructions(), delivered.routeTopology(),
                delivered.strategicPlans(), delivered.humanPopulation(), delivered.companies(), delivered.resourceSites());
    }

    private static FrontierWorldState completedDelivery(WorldId world) {
        FrontierWorldState state = initial(world); RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-2"));
        SupplyContract contract = state.contracts().get(new SubjectId("contract:supply-1-2"));
        SubjectId item = state.inventory().cargo().get(operation.cargoId()).itemIds().getFirst();
        SubjectId receiver = FrontierCargoValidation.receiverStore(state.bootstrap(), operation);
        ExactInventory inventory = state.inventory().completeCargoHandoff(operation.cargoId(), List.of(new CargoHandoffPlacement(item,
                new InventoryCustody.ContainerSlot(receiver, state.inventory().firstFreeSlot(receiver).orElseThrow()))));
        Map<SubjectId, SupplyContract> contracts = new LinkedHashMap<>(state.contracts()); contracts.put(contract.id(), contract.withStatus(ContractStatus.DELIVERED));
        RouteOperation completed = new RouteOperation(operation.id(), operation.settlementId(), operation.cargoId(), operation.destinationId(),
                operation.unit(), operation.route(), 0, OperationStage.COMPLETED, java.util.Optional.empty(), java.util.Optional.empty());
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
    private static FrontierWorldState initial(WorldId world) { return FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 91L).initialState(); }
}
