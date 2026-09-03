package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SupplyOperationProcessTest {
    @Test
    void missingBreadBlocksThePreparationAndItsDependentDelivery() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:supply-blocked"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:supply"), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask preparation = new StrategicTask(new SubjectId("task:supply-prepare"), objective.id(), settlement.id(), StrategicTaskKind.PREPARE_BREAD_CARGO,
                Optional.empty(), List.of(StrategicTaskRequirement.EXACT_BREAD_CARGO), List.of(), StrategicTaskStatus.PENDING);
        StrategicTask delivery = new StrategicTask(new SubjectId("task:supply-deliver"), objective.id(), settlement.id(), StrategicTaskKind.DELIVER_BREAD_TO_HIVE,
                Optional.empty(), List.of(StrategicTaskRequirement.PASSABLE_SUPPLY_ROUTE, StrategicTaskRequirement.AVAILABLE_HAULER,
                StrategicTaskRequirement.AVAILABLE_GUARD), List.of(preparation.id()), StrategicTaskStatus.PENDING);
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(preparation).addTask(delivery));

        List<ProposedEvent> planned = SupplyOperationProcess.planStart(state, SupplyOperationProcess.start(preparation, 100L));

        assertEquals(List.of(new StrategicTaskTransition(preparation.id(), StrategicTaskStatus.BLOCKED),
                new StrategicTaskTransition(delivery.id(), StrategicTaskStatus.BLOCKED)), planned.stream().map(ProposedEvent::payload).toList());
        FrontierWorldState reduced = StrategicObjectiveProcess.reduceTaskTransition(state, settlement.id(), (StrategicTaskTransition) planned.getFirst().payload());
        reduced = StrategicObjectiveProcess.reduceTaskTransition(reduced, settlement.id(), (StrategicTaskTransition) planned.get(1).payload());
        assertEquals(StrategicObjectiveStatus.BLOCKED, reduced.strategicPlans().objectives().get(objective.id()).status());
    }

    @Test
    void unknownHotLeaseDefersColdRouteProgressWithoutPretendingTheSceneIsActive() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(
                new WorldId("frontier:supply-unknown-scene"), 91L));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-2"));
        SceneLeaseId leaseId = new SceneLeaseId("lease:supply-unknown-scene");
        SceneLease lease = FrontierTestSceneLeases.exact(before, leaseId, operation.id(), operation.cargoId(),
                operation.currentPosition(), engine.checkpoint().instant(), engine.checkpoint().revision().value(),
                Optional.empty(), operation.participantIds());
        FrontierWorldState unknown = before.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART);

        long due = engine.checkpoint().instant().ticks() + 1L;
        List<ProposedEvent> planned = SupplyOperationProcess.planProgress(unknown, SupplyOperationProcess.operationProgress(operation, due));

        ScheduleEffect.Rescheduled deferred = assertInstanceOf(ScheduleEffect.Rescheduled.class, planned.getFirst().payload());
        assertEquals(1, planned.size());
        assertEquals(SupplyOperationProcess.operationProgress(operation, due + 100L), deferred.replacement());
    }

    @Test
    void observedMissingRestartSceneBlocksOnlyItsExactDeliveryRatherThanReschedulingForever() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(
                new WorldId("frontier:supply-unresolved-scene"), 91L));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-2"));
        SceneLeaseId leaseId = new SceneLeaseId("lease:supply-unresolved-scene");
        SceneLease lease = FrontierTestSceneLeases.exact(before, leaseId, operation.id(), operation.cargoId(),
                operation.currentPosition(), engine.checkpoint().instant(), engine.checkpoint().revision().value(),
                Optional.empty(), operation.participantIds());
        FrontierWorldState unresolved = FrontierSceneLeaseStateSupport.recoveryUnresolved(
                before.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART),
                new SceneLeaseRecoveryUnresolved(leaseId, Set.of(operation.participantIds().getFirst()), false));

        List<ProposedEvent> planned = SupplyOperationProcess.planProgress(unresolved,
                SupplyOperationProcess.operationProgress(operation, engine.checkpoint().instant().ticks() + 1L));

        assertEquals(List.of(new OperationFailed(operation.id(), "scene-recovery-unresolved"),
                new StrategicTaskTransition(new SubjectId("task:settlement-1-settlement_deliver_bread_to_hive-2-deliver"), StrategicTaskStatus.BLOCKED)),
                planned.stream().map(ProposedEvent::payload).toList());
        assertEquals(unresolved, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unresolved)));
        SceneLeaseRecoveryUnresolved payload = new SceneLeaseRecoveryUnresolved(leaseId, Set.of(operation.participantIds().getFirst()), false);
        assertEquals(payload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
    }

    @Test
    void obsoleteProgressActionIsDurablyCancelledAfterItsOperationHasAlreadyFailed() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(
                new WorldId("frontier:supply-terminal-progress"), 91L));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-2"));
        SceneLeaseId leaseId = new SceneLeaseId("lease:supply-terminal-progress");
        SceneLease lease = FrontierTestSceneLeases.exact(before, leaseId, operation.id(), operation.cargoId(),
                operation.currentPosition(), engine.checkpoint().instant(), engine.checkpoint().revision().value(),
                Optional.empty(), operation.participantIds());
        FrontierWorldState failed = FrontierSceneLeaseStateSupport.recoveryUnresolved(
                before.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART),
                new SceneLeaseRecoveryUnresolved(leaseId, Set.of(operation.participantIds().getFirst()), false));
        failed = failed.failOperation(operation.id());
        ScheduledAction action = SupplyOperationProcess.operationProgress(operation, engine.checkpoint().instant().ticks() + 1L);

        List<ProposedEvent> planned = SupplyOperationProcess.planProgress(failed, action);

        ScheduleEffect.Cancelled cancelled = assertInstanceOf(ScheduleEffect.Cancelled.class, planned.getFirst().payload());
        assertEquals(action.id(), cancelled.scheduleId());
        assertEquals(1, planned.size());
    }

    @Test
    void activeDepotLoadsCargoOnlyAfterExactPhysicalRemovalReceiptAndBlocksOnUnknownOutcome() {
        FrontierWorldState state = loadingState("frontier:cargo-loading");
        SupplyContract contract = state.contracts().values().iterator().next();
        ScheduledAction action = new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:cargo-loading-test"),
                new SimInstant(500L), 0, contract.id(), "frontier.supply.cargo.load", 1);

        List<ProposedEvent> prepared = SupplyOperationProcess.planCargoLoad(state, action, false);
        assertEquals(1, prepared.size(), "an active owned depot must not transfer custody before Minecraft removes the stack");
        PhysicalIntent intent = assertInstanceOf(PhysicalIntentPrepared.class, prepared.getFirst().payload()).intent();
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.CARGO_LOADING, intent.kind());
        state = state.preparePhysicalIntent(intent).transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        ExactItemStack item = state.inventory().items().get(intent.subjectIds().get(2));
        CargoLoadObservation receipt = new CargoLoadObservation(new PhysicalObservationId("observation:cargo-loading-test"), intent.id(), contract.id(),
                contract.cargoId(), item.id(), item.count());
        PhysicalIntentTransition receiptTransition = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(receiptTransition, codecs.decode(receiptTransition.type(), codecs.encode(receiptTransition)),
                "the exact physical removal receipt survives the ordered WAL payload boundary");

        List<ProposedEvent> confirmed = SupplyOperationProcess.planCargoLoadingTransition(state, intent,
                receiptTransition, 501L);
        assertInstanceOf(PhysicalIntentTransition.class, confirmed.getFirst().payload());
        assertEquals(PhysicalIntentStatus.CONFIRMED, ((PhysicalIntentTransition) confirmed.getFirst().payload()).status());
        assertInstanceOf(CargoLoaded.class, confirmed.get(1).payload());
        assertEquals(new InventoryCustody.ContainerSlot(FrontierWorldState.depotId(contract.settlementId()), 1), item.custody(),
                "before confirmed receipt the canonical stack remains in its exact depot slot");

        state = state.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        state = state.loadContractCargo(contract.id(), new CargoBatch(contract.cargoId(), contract.settlementId(), List.of(item.id())));
        assertEquals(new InventoryCustody.Cargo(contract.cargoId()), state.inventory().items().get(item.id()).custody());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)),
                "the exact removal receipt and cargo transfer survive snapshot recovery");

        FrontierWorldState blocked = loadingState("frontier:cargo-loading-unknown");
        SupplyContract blockedContract = blocked.contracts().values().iterator().next();
        PhysicalIntent blockedIntent = assertInstanceOf(PhysicalIntentPrepared.class,
                SupplyOperationProcess.planCargoLoad(blocked, actionFor(blockedContract), false).getFirst().payload()).intent();
        blocked = blocked.preparePhysicalIntent(blockedIntent);
        List<ProposedEvent> unknown = SupplyOperationProcess.planCargoLoadingTransition(blocked, blockedIntent,
                new PhysicalIntentTransition(blockedIntent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()), 501L);
        assertEquals(List.of(PhysicalIntentTransition.class, StrategicTaskTransition.class, StrategicTaskTransition.class),
                unknown.stream().map(event -> event.payload().getClass()).toList());
        assertEquals(StrategicTaskStatus.BLOCKED, ((StrategicTaskTransition) unknown.get(1).payload()).status());
        assertEquals(StrategicTaskStatus.BLOCKED, ((StrategicTaskTransition) unknown.get(2).payload()).status());
    }

    @Test
    void preEffectCargoFailureDurablyAbandonsItsExactOrderedContractInsteadOfLeakingIt() {
        FrontierWorldState state = loadingState("frontier:cargo-abandoned");
        SupplyContract contract = state.contracts().values().iterator().next();
        state = state.withHumanPopulation(state.humanPopulation().transitionQuarantine(contract.settlementId(), SettlementQuarantineStatus.QUARANTINED, 500L));

        List<ProposedEvent> planned = SupplyOperationProcess.planCargoLoad(state, actionFor(contract), false);

        assertEquals(List.of(SupplyContractAbandoned.class, StrategicTaskTransition.class, StrategicTaskTransition.class),
                planned.stream().map(event -> event.payload().getClass()).toList());
        SupplyContractAbandoned abandoned = assertInstanceOf(SupplyContractAbandoned.class, planned.getFirst().payload());
        assertEquals(contract.id(), abandoned.contractId());
        assertEquals(abandoned, FrontierWorldRuntimeDefinition.payloadCodecs().decode(abandoned.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(abandoned)));

        FrontierWorldState reduced = state.abandonOrderedSupplyContract(abandoned.contractId());
        assertEquals(null, reduced.contracts().get(contract.id()));
        assertEquals(contract.id(), state.contracts().get(contract.id()).id(), "the pre-effect source snapshot stays exact");
    }

    private static FrontierWorldState loadingState(String world) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId(world), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst(); SubjectId depot = FrontierWorldState.depotId(settlement.id());
        ExactItemStack bread = new ExactItemStack(new SubjectId("item:cargo-loading-bread"), settlement.id(), "minecraft:bread", 64,
                new InventoryCustody.ContainerSlot(depot, 1));
        state = state.withInventory(state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE).store(bread));
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:cargo-loading"), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask preparation = new StrategicTask(new SubjectId("task:cargo-loading-prepare"), objective.id(), settlement.id(),
                StrategicTaskKind.PREPARE_BREAD_CARGO, Optional.empty(), List.of(StrategicTaskRequirement.EXACT_BREAD_CARGO), List.of(), StrategicTaskStatus.ACTIVE);
        StrategicTask delivery = new StrategicTask(new SubjectId("task:cargo-loading-deliver"), objective.id(), settlement.id(),
                StrategicTaskKind.DELIVER_BREAD_TO_HIVE, Optional.empty(), List.of(StrategicTaskRequirement.PASSABLE_SUPPLY_ROUTE,
                StrategicTaskRequirement.AVAILABLE_HAULER, StrategicTaskRequirement.AVAILABLE_GUARD), List.of(preparation.id()), StrategicTaskStatus.PENDING);
        return state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(preparation).addTask(delivery))
                .createSupplyContract(new SupplyContract(new SubjectId("contract:supply-1-1"), settlement.id(), state.bootstrap().hive().id(),
                        new SubjectId("cargo:supply-1-1"), "minecraft:bread", 64, ContractStatus.ORDERED));
    }

    private static ScheduledAction actionFor(SupplyContract contract) {
        return new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:cargo-loading-unknown"),
                new SimInstant(500L), 0, contract.id(), "frontier.supply.cargo.load", 1);
    }
}
