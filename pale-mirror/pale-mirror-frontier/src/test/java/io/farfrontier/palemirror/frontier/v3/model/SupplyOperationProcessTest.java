package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertEquals(List.of(TerminalDiagnosticProducer.operationFailed(operation.id(), "scene-recovery-unresolved"),
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
        ExactItemStack item = state.inventory().items().get(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.SOURCE_ITEM));
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
    void composedCargoConfirmationCreatesItsRouteAfterPhysicalRemovalWithoutQuarantiningTheWorld() {
        WorldId world = new WorldId("frontier:cargo-loading-composed-confirmation");
        FrontierWorldState initial = loadingState(world.value());
        SupplyContract contract = initial.contracts().values().iterator().next();
        PhysicalIntent intent = assertInstanceOf(PhysicalIntentPrepared.class,
                SupplyOperationProcess.planCargoLoad(initial, actionFor(contract), false).getFirst().payload()).intent();
        var engine = cargoEngine(world, prepared(initial, contract.settlementId(), intent));

        assertInstanceOf(CommandResult.Accepted.class, submit(engine, world, "running",
                new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty())));
        FrontierWorldState running = state(engine);
        ExactItemStack item = running.inventory().items().get(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.SOURCE_ITEM));
        CargoLoadObservation receipt = new CargoLoadObservation(new PhysicalObservationId("observation:cargo-loading-composed"), intent.id(),
                contract.id(), contract.cargoId(), item.id(), item.count());

        CommandResult confirmation = submit(engine, world, "confirmed",
                new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)));
        assertInstanceOf(CommandResult.Accepted.class, confirmation, confirmation::toString);
        FrontierWorldState confirmed = state(engine);
        assertEquals(ContractStatus.LOADED, confirmed.contracts().get(contract.id()).status());
        assertTrue(confirmed.inventory().cargo().containsKey(contract.cargoId()));
        assertTrue(confirmed.operations().values().stream().anyMatch(operation -> operation.contractId().equals(contract.id())
                && operation.cargoId().equals(contract.cargoId())), "the confirmed removal must create the exact later route authority");
        assertEquals(PhysicalIntentStatus.CONFIRMED, confirmed.physicalIntents().get(intent.id()).status());
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind(),
                "a lawful physical confirmation must not quarantine the unrelated world");
    }

    @Test
    void ambiguousCargoRemovalRetainsOnlyItsContractRecoveryWithoutQuarantiningTheWorld() {
        WorldId world = new WorldId("frontier:cargo-loading-composed-ambiguous");
        FrontierWorldState initial = loadingState(world.value());
        SupplyContract contract = initial.contracts().values().iterator().next();
        PhysicalIntent intent = assertInstanceOf(PhysicalIntentPrepared.class,
                SupplyOperationProcess.planCargoLoad(initial, actionFor(contract), false).getFirst().payload()).intent();
        var engine = cargoEngine(world, prepared(initial, contract.settlementId(), intent));

        assertInstanceOf(CommandResult.Accepted.class, submit(engine, world, "running",
                new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty())));
        CommandResult ambiguousResult = submit(engine, world, "ambiguous",
                new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()));
        assertInstanceOf(CommandResult.Accepted.class, ambiguousResult, ambiguousResult::toString);
        FrontierWorldState ambiguous = state(engine);
        assertEquals(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, ambiguous.physicalIntents().get(intent.id()).status());
        assertEquals(ContractStatus.ORDERED, ambiguous.contracts().get(contract.id()).status());
        assertTrue(ambiguous.operations().isEmpty(), "an ambiguous source removal must not invent a route operation");
        assertEquals(StrategicTaskStatus.BLOCKED, ambiguous.strategicPlans().tasks().values().stream()
                .filter(task -> task.id().value().contains("cargo-loading-prepare")).findFirst().orElseThrow().status());
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind(),
                "one ambiguous cargo owner must remain local recovery, not a world quarantine");
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

    private static io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> cargoEngine(WorldId world,
                                                                                                                   FrontierWorldState initial) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        return FrontierEngines.create(new FrontierEngineConfiguration<>(world, initial, SimInstant.ZERO, base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), List.of(),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter()));
    }

    private static FrontierWorldState prepared(FrontierWorldState state, SubjectId owner, PhysicalIntent intent) {
        return FrontierWorldRuntimeDefinition.reduce(state, new FrontierEvent(1, new EventId("event:cargo-loading-prepared"),
                new TransactionId("transaction:cargo-loading-prepared"), state.bootstrap().worldId(), Revision.ZERO, SimInstant.ZERO,
                owner, CauseChain.root(new CommandId("command:cargo-loading-prepared")), new PhysicalIntentPrepared(intent)));
    }

    private static CommandResult submit(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine,
                                        WorldId world, String suffix, PhysicalIntentTransition transition) {
        var checkpoint = engine.checkpoint(); CommandId id = new CommandId("command:cargo-loading-" + suffix);
        return engine.submit(new FrontierCommand(1, id, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), transition));
    }

    private static FrontierWorldState state(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine) {
        return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }
}
