package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionProcessTest {
    @Test
    void coldProductionStillAdvancesWithoutMaterializingAnUnloadedContainer() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:production"), 91L));
        for (long tick = 100L; tick <= 2_200L; tick += 100L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState completed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(completed.productionJobs().isEmpty());
        ExactItemStack bread = completed.inventory().items().get(new SubjectId("item:production-1-1-bread"));
        assertEquals("minecraft:bread", bread.itemKind());
        assertEquals(64, bread.count());
        assertEquals(new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 0), bread.custody());
        assertEquals(StrategicTaskStatus.COMPLETED, completed.strategicPlans().tasks().values().stream()
                .filter(task -> task.kind() == StrategicTaskKind.PRODUCE_BREAD).findFirst().orElseThrow().status());
        MarketDemand demand = completed.companies().market().demands().values().stream().filter(value -> value.buyerId().equals(new SubjectId("settlement:1")))
                .findFirst().orElseThrow();
        assertEquals(MarketDemandStatus.FULFILLED, demand.status());
        assertEquals(MarketWorkOrderStatus.FULFILLED, completed.companies().market().workOrders().values().stream()
                .filter(order -> order.demandId().equals(demand.id())).findFirst().orElseThrow().status());
        assertTrue(completed.inventory().economics().reservations().isEmpty());
    }

    @Test
    void activeMaterializedProductionRetainsInputUntilOneDurablePhysicalTransformationConfirmsOutput() {
        PreparedProduction prepared = activePhysicalProduction();
        ExactItemStack input = prepared.state().inventory().items().get(prepared.job().consumedItemId());
        FrontierWorldState running = prepared.state().transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        ProductionTransformationObservation receipt = new ProductionTransformationObservation(new PhysicalObservationId("observation:test-production"), prepared.intent().id(),
                input.id(), prepared.job().outputItemId(), input.count(), prepared.job().outputCount());
        PhysicalIntentTransition transition = new PhysicalIntentTransition(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(receipt, ((PhysicalIntentTransition) FrontierWorldRuntimeDefinition.payloadCodecs().decode(transition.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(transition))).observation().orElseThrow());
        FrontierWorldState completed = running.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertTrue(completed.productionJobs().isEmpty());
        assertEquals("minecraft:bread", completed.inventory().items().get(prepared.job().outputItemId()).itemKind());
        assertEquals(FixedScalar.ONE, completed.inventory().economics().require(prepared.job().workerId()).balance());
        assertEquals(FixedScalar.ONE, completed.inventory().economics().require(CompanyFoundationProcess.companyId(prepared.job().settlementId())).balance());
        assertEquals(MarketWorkOrderStatus.FULFILLED, completed.companies().market().workOrders().values().stream()
                .filter(order -> order.jobId().equals(prepared.job().id())).findFirst().orElseThrow().status());
    }

    @Test
    void deathRevokesNewWorkButSettlesAnAlreadyRunningPhysicalTransformationExactlyOnce() {
        PreparedProduction prepared = activePhysicalProduction();
        WorldId world = new WorldId("frontier:production-physical");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, prepared.state(), SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        assertInstanceOf(CommandResult.Accepted.class, submitTransition(engine, world, "worker-running-before-death", prepared.intent().id(),
                PhysicalIntentStatus.RUNNING, Optional.empty()));
        BlockPosition position = prepared.state().actorLocations().get(prepared.job().workerId()).position();
        CommandId deathId = new CommandId("command:production-worker-died-after-effect-prepared");

        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, deathId, world, engine.checkpoint().revision(), engine.checkpoint().instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(deathId),
                new AmbientActorDied(prepared.job().workerId(), position, "entity:test-explosion"))));
        FrontierWorldState afterDeath = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        EmploymentContract terminated = afterDeath.companies().employmentContracts().get(CompanyFoundationProcess.employmentId(prepared.settlementId()));
        assertEquals(ActorLifeStatus.DEAD, afterDeath.actorLocations().get(prepared.job().workerId()).condition().status());
        assertEquals(EmploymentContractStatus.TERMINATED, terminated.status());
        assertTrue(afterDeath.productionJobs().containsKey(prepared.job().id()));
        assertTrue(afterDeath.inventory().economics().reservations().containsKey(prepared.order().reservationId()));
        assertEquals(PhysicalIntentStatus.RUNNING, afterDeath.physicalIntents().get(prepared.intent().id()).status());
        assertTrue(CompanyWorkPaymentProcess.contractFor(afterDeath, prepared.job()).isEmpty());
        assertTrue(CompanyWorkPaymentProcess.settlementContractFor(afterDeath, prepared.job()).isPresent());
        assertFalse(CompanyFoundationProcess.plan(afterDeath, CompanyFoundationProcess.review(prepared.settlementId(), 2, 28_000L)).stream()
                .map(ProposedEvent::payload).anyMatch(EmploymentContractOpened.class::isInstance));

        ExactItemStack input = afterDeath.inventory().items().get(prepared.job().consumedItemId());
        ProductionTransformationObservation receipt = new ProductionTransformationObservation(new PhysicalObservationId("observation:production-dead-worker-confirmed"),
                prepared.intent().id(), input.id(), prepared.job().outputItemId(), input.count(), prepared.job().outputCount());
        assertInstanceOf(CommandResult.Accepted.class, submitTransition(engine, world, "dead-worker-confirmed", prepared.intent().id(),
                PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)));

        FrontierWorldState completed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(completed.productionJobs().isEmpty());
        assertTrue(completed.inventory().economics().reservations().isEmpty());
        assertEquals(1L, completed.companies().employmentContracts().get(terminated.id()).completedJobs());
        assertEquals(EmploymentContractStatus.TERMINATED, completed.companies().employmentContracts().get(terminated.id()).status());
        assertEquals(FixedScalar.ONE, completed.inventory().economics().require(prepared.job().workerId()).balance());
        assertEquals(MarketWorkOrderStatus.FULFILLED, completed.companies().market().workOrders().get(prepared.order().id()).status());
    }

    @Test
    void deathBeforeColdCompletionCancelsMarketWorkAndReturnsItsExactInput() {
        ColdMarketJob prepared = coldMarketJob();
        WorldId world = new WorldId("frontier:production-cold-cancel");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, prepared.state(), SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        BlockPosition position = prepared.state().actorLocations().get(prepared.job().workerId()).position();
        CommandId deathId = new CommandId("command:cold-production-worker-died");
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, deathId, world, engine.checkpoint().revision(), engine.checkpoint().instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(deathId),
                new AmbientActorDied(prepared.job().workerId(), position, "entity:test-explosion"))));
        FrontierWorldState released = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());

        assertEquals(EmploymentContractStatus.TERMINATED, released.companies().employmentContracts()
                .get(CompanyFoundationProcess.employmentId(prepared.settlementId())).status());
        assertFalse(released.productionJobs().containsKey(prepared.job().id()));
        assertEquals(prepared.input(), released.inventory().items().get(prepared.input().id()));
        assertTrue(released.inventory().economics().reservations().isEmpty());
        assertEquals(MarketWorkOrderStatus.CANCELLED, released.companies().market().workOrders().get(prepared.order().id()).status());
    }

    @Test
    void deathBeforeMaterializedTransformRemovesPreparedIntentAndReturnsTheExactInput() {
        PreparedProduction prepared = activePhysicalProduction();
        WorldId world = new WorldId("frontier:production-physical");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, prepared.state(), SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        BlockPosition position = prepared.state().actorLocations().get(prepared.job().workerId()).position();
        CommandId deathId = new CommandId("command:prepared-production-worker-died");

        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, deathId, world, engine.checkpoint().revision(), engine.checkpoint().instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(deathId),
                new AmbientActorDied(prepared.job().workerId(), position, "entity:test-explosion"))));
        FrontierWorldState cancelled = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());

        assertEquals(EmploymentContractStatus.TERMINATED, cancelled.companies().employmentContracts()
                .get(CompanyFoundationProcess.employmentId(prepared.settlementId())).status());
        assertTrue(cancelled.productionJobs().isEmpty());
        assertFalse(cancelled.physicalIntents().containsKey(prepared.intent().id()));
        assertEquals(prepared.state().inventory().items().get(prepared.job().consumedItemId()),
                cancelled.inventory().items().get(prepared.job().consumedItemId()));
        assertTrue(cancelled.inventory().economics().reservations().isEmpty());
        assertEquals(MarketWorkOrderStatus.CANCELLED, cancelled.companies().market().workOrders().get(prepared.order().id()).status());
        assertEquals(StrategicTaskStatus.BLOCKED, cancelled.strategicPlans().tasks().get(prepared.taskId()).status());
    }

    @Test
    void workerDeathDisposableFixtureKeepsTheExactCrafterAsTheOnlyNearbyAmbientActor() {
        FrontierWorldState state = FrontierV3FixtureCatalog.productionWorkerDeathConfiguration(
                new WorldId("frontier:production-worker-death-fixture"), 41L).initialState();
        SubjectId worker = state.productionJobs().get(new SubjectId("job:development-production-input-theft")).workerId();
        BlockPosition target = new BlockPosition(-480, 64, -480);

        assertEquals(new SubjectId("resident:1-15"), worker);
        assertEquals(target, state.actorLocations().get(worker).position());
        assertTrue(state.actorLocations().entrySet().stream().filter(entry -> !entry.getKey().equals(worker))
                .noneMatch(entry -> Math.max(Math.abs(entry.getValue().position().x() - target.x()),
                        Math.abs(entry.getValue().position().z() - target.z())) <= 96));
    }

    @Test
    void coldOrderCancelledBeforeAnEffectReturnsTheSameInputAndReleasesItsExactReservation() {
        ColdMarketJob prepared = coldMarketJob();
        FrontierWorldState unavailable = prepared.state().withStructureCondition(prepared.job().facilityId(), StructureCondition.DESTROYED);

        List<ProposedEvent> planned = ProductionProcess.planCompletion(unavailable, new ScheduledAction(
                new ScheduleId("schedule:test-cold-cancel"), new SimInstant(100L), 0, prepared.job().id(), "frontier.settlement.production.task.complete", 1));

        ProductionBlocked blocked = assertInstanceOf(ProductionBlocked.class, planned.getFirst().payload());
        MarketWorkOrderCancelled cancelled = assertInstanceOf(MarketWorkOrderCancelled.class, planned.get(1).payload());
        assertEquals(ProductionBlockReason.FACILITY_UNAVAILABLE, blocked.reason());
        assertEquals(prepared.order().id(), cancelled.orderId());
        FrontierWorldState blockedState = ProductionProcess.reduceBlocked(unavailable, prepared.settlementId(), blocked);
        FrontierWorldState released = MarketClearingProcess.reduceWorkOrderCancelled(blockedState, prepared.settlementId(), cancelled);

        assertFalse(released.productionJobs().containsKey(prepared.job().id()));
        assertEquals(prepared.input(), released.inventory().items().get(prepared.input().id()));
        assertTrue(released.inventory().economics().reservations().isEmpty());
        assertEquals(MarketWorkOrderStatus.CANCELLED, released.companies().market().workOrders().get(prepared.order().id()).status());
        assertEquals(MarketDemandStatus.CANCELLED, released.companies().market().demands().get(prepared.order().demandId()).status());
        assertEquals(StrategicTaskStatus.BLOCKED, released.strategicPlans().tasks().get(prepared.order().taskId()).status());
        assertEquals(released, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(released)));
        ProductionStarted started = new ProductionStarted(prepared.job(), prepared.input().id());
        assertEquals(started, FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
    }

    @Test
    void settlementDefenceInterruptsOnlyColdWorkAndReleasesItsExactCivilianCommitment() {
        ColdMarketJob prepared = coldMarketJob(); FrontierWorldState state = prepared.state();
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), prepared.settlementId());
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow();
        state = state.withActorLocation(scout.id(), settlement.anchor());
        HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement.id(), scout.id(), settlement.anchor(), 100L);
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:defence-interrupt"), hive,
                StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT, Optional.empty(), 7, StrategicObjectiveStatus.ACTIVE);
        StrategicTask assault = new StrategicTask(new SubjectId("task:defence-interrupt"), objective.id(), hive,
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD,
                StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.PENDING);
        StrategicPlanState plans = state.strategicPlans().withHiveDoctrine(new HiveDoctrineState(HiveDoctrine.INTERDICT, 100L))
                .withHiveSettlementKnowledge(new HiveSettlementKnowledge(java.util.Map.of(settlement.id(), sighting))).addObjective(objective).addTask(assault);
        state = StrategicObjectiveProcess.reduceTaskTransition(state.withStrategicPlans(plans), hive,
                new StrategicTaskTransition(assault.id(), StrategicTaskStatus.ACTIVE));
        ProductionInterrupted interrupted = new ProductionInterrupted(prepared.job().id(), prepared.job().workerId(), assault.id(), sighting);

        assertEquals(interrupted, FrontierWorldRuntimeDefinition.payloadCodecs().decode(interrupted.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(interrupted)));
        assertEquals(prepared.job(), ProductionProcess.interruptibleForSettlementDefence(state, prepared.job().workerId()).orElseThrow());
        FrontierWorldState released = ProductionProcess.reduceInterrupted(state, prepared.settlementId(), 100L, interrupted);

        assertFalse(released.productionJobs().containsKey(prepared.job().id()));
        assertEquals(prepared.input(), released.inventory().items().get(prepared.input().id()));
        assertTrue(released.inventory().economics().reservations().isEmpty());
        assertEquals(MarketWorkOrderStatus.CANCELLED, released.companies().market().workOrders().get(prepared.order().id()).status());
        assertEquals(StrategicTaskStatus.BLOCKED, released.strategicPlans().tasks().get(prepared.order().taskId()).status());
        assertEquals(HumanAssignmentKind.IDLE, HumanAssignmentProjection.compile(released).assignment(prepared.job().workerId()).kind());
        assertEquals(released, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(released)));
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceInterrupted(released, prepared.settlementId(), 100L, interrupted));
    }

    @Test
    void settlementDefenceCannotInterruptMaterializedOrPreparedProduction() {
        PreparedProduction prepared = activePhysicalProduction();
        ProductionInterrupted interruption = new ProductionInterrupted(prepared.job().id(), prepared.job().workerId(),
                new SubjectId("task:foreign-assault"), new HiveSettlementKnowledge.Sighting(prepared.settlementId(),
                prepared.state().bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow().id(),
                FrontierWorldStateSupport.settlement(prepared.state().bootstrap(), prepared.settlementId()).anchor(), 1L));

        assertTrue(ProductionProcess.interruptibleForSettlementDefence(prepared.state(), prepared.job().workerId()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceInterrupted(prepared.state(), prepared.settlementId(), 1L, interruption));
    }

    @Test
    void marketOrderWithAPreparedPhysicalTransformationCannotBeCancelledToFreeFunds() {
        PreparedProduction prepared = activePhysicalProduction();
        FrontierWorldState unavailable = prepared.state().withStructureCondition(prepared.job().facilityId(), StructureCondition.DESTROYED);
        MarketWorkOrder order = unavailable.companies().market().workOrders().values().stream()
                .filter(value -> value.jobId().equals(prepared.job().id())).findFirst().orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> MarketClearingProcess.reduceWorkOrderCancelled(unavailable, prepared.job().settlementId(),
                new MarketWorkOrderCancelled(order.id(), prepared.job().id(), ProductionBlockReason.FACILITY_UNAVAILABLE)));
        assertTrue(unavailable.inventory().economics().reservations().containsKey(order.reservationId()));
    }

    @Test
    void playerTakingMaterializedInputBeforeIntentAtomicallyCancelsTheExactMarketWork() {
        MaterializedProduction prepared = activeMaterializedProduction();
        WorldId world = new WorldId("frontier:production-physical");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, prepared.state(), SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        ExactItemStack input = prepared.state().inventory().items().get(prepared.job().consumedItemId());
        ExactItemCustodyChanged departure = new ExactItemCustodyChanged(input.id(), input.custody(), new InventoryCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000064")));

        var checkpoint = engine.checkpoint();
        CommandResult result = engine.submit(new FrontierCommand(1, new CommandId("command:production-player-takes-input"), world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(new CommandId("command:production-player-takes-input")), departure));

        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
        FrontierWorldState cancelled = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(cancelled.productionJobs().isEmpty());
        assertEquals(new InventoryCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000064")), cancelled.inventory().items().get(input.id()).custody());
        assertTrue(cancelled.inventory().economics().reservations().isEmpty());
        assertEquals(MarketWorkOrderStatus.CANCELLED, cancelled.companies().market().workOrders().get(prepared.order().id()).status());
        assertEquals(StrategicTaskStatus.BLOCKED, cancelled.strategicPlans().tasks().get(prepared.taskId()).status());
    }

    @Test
    void playerTakingMaterializedInputAfterPreparedIntentRetainsUnresolvedPhysicalWork() {
        PreparedProduction prepared = activePhysicalProduction();
        WorldId world = new WorldId("frontier:production-physical");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, prepared.state(), SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        ExactItemStack input = prepared.state().inventory().items().get(prepared.job().consumedItemId());
        ExactItemCustodyChanged departure = new ExactItemCustodyChanged(input.id(), input.custody(), new InventoryCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000065")));

        var checkpoint = engine.checkpoint();
        CommandResult result = engine.submit(new FrontierCommand(1, new CommandId("command:production-player-takes-prepared-input"), world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(new CommandId("command:production-player-takes-prepared-input")), departure));

        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
        FrontierWorldState unresolved = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(unresolved.productionJobs().containsKey(prepared.job().id()));
        assertEquals(new InventoryCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000065")), unresolved.inventory().items().get(input.id()).custody());
        assertTrue(unresolved.inventory().economics().reservations().containsKey(prepared.order().reservationId()));
        assertEquals(PhysicalIntentStatus.PREPARED, unresolved.physicalIntents().get(prepared.intent().id()).status());
    }

    @Test
    void coldInputHoldReservesItsOriginalSlotFromOtherCanonicalOutput() {
        ColdMarketJob prepared = coldMarketJob();
        InventoryCustody.ContainerSlot source = (InventoryCustody.ContainerSlot) prepared.input().custody();
        SubjectId intruderId = new SubjectId("item:must-not-overwrite-cold-hold");
        ExactItemStack intruder = new ExactItemStack(intruderId, prepared.settlementId(), "minecraft:wheat", 64, source);

        assertTrue(prepared.state().productionHoldReserves(source));
        assertFalse(prepared.state().containerSlotAvailable(source));
        assertEquals(1, prepared.state().firstFreeContainerSlot(source.containerId()).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> prepared.state().withInventory(prepared.state().inventory().store(intruder)));
    }

    @Test
    void trustedPhysicalExecutorRoutesProductionTransitionsToTheirSettlementJob() {
        PreparedProduction prepared = activePhysicalProduction();
        WorldId world = new WorldId("frontier:production-command");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, prepared.state(), SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));

        assertInstanceOf(CommandResult.Accepted.class, submitTransition(engine, world, "running", prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty()));
        FrontierWorldState running = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(PhysicalIntentStatus.RUNNING, running.physicalIntents().get(prepared.intent().id()).status());

        ExactItemStack input = running.inventory().items().get(prepared.job().consumedItemId());
        ProductionTransformationObservation receipt = new ProductionTransformationObservation(new PhysicalObservationId("observation:production-command"), prepared.intent().id(),
                input.id(), prepared.job().outputItemId(), input.count(), prepared.job().outputCount());
        CommandResult confirmed = submitTransition(engine, world, "confirmed", prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertInstanceOf(CommandResult.Accepted.class, confirmed, confirmed.toString());
        FrontierWorldState completed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals("minecraft:bread", completed.inventory().items().get(prepared.job().outputItemId()).itemKind());
    }

    @Test
    void foreignOrMissingPhysicalInputBlocksTheTaskWithoutDiscardingCanonicalClaim() {
        PreparedProduction prepared = activePhysicalProduction();

        FrontierWorldState blocked = prepared.state().transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());

        assertEquals(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, blocked.physicalIntents().get(prepared.intent().id()).status());
        assertTrue(blocked.inventory().items().containsKey(new SubjectId("item:bootstrap-1-wheat")));
        assertEquals(StrategicTaskStatus.BLOCKED, blocked.strategicPlans().tasks().values().stream()
                .filter(task -> task.kind() == StrategicTaskKind.PRODUCE_BREAD).findFirst().orElseThrow().status());
    }

    @Test
    void productionRefusesToStartWithoutAnExactInputStack() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:input"), 91L));
        FrontierWorldState withoutInput = productionTask(initial.withInventory(initial.inventory().withoutItem(new SubjectId("item:bootstrap-1-wheat"))), StrategicTaskStatus.PENDING);
        StrategicTask task = withoutInput.strategicPlans().tasks().values().iterator().next();

        List<ProposedEvent> planned = FrontierWorldRuntimeDefinition.planScheduled(withoutInput, ProductionProcess.start(task, 200L));

        ProductionBlocked event = assertInstanceOf(ProductionBlocked.class, planned.getFirst().payload());
        assertEquals(ProductionBlockReason.INPUT_UNAVAILABLE, event.reason());
        FrontierWorldState reduced = ProductionProcess.reduceBlocked(withoutInput, withoutInput.bootstrap().settlements().getFirst().id(), event);
        reduced = StrategicObjectiveProcess.reduceTaskTransition(reduced, event.settlementId(), assertInstanceOf(StrategicTaskTransition.class, planned.get(1).payload()));
        assertEquals(StrategicTaskStatus.BLOCKED, reduced.strategicPlans().tasks().get(task.id()).status());
        assertEquals(StrategicObjectiveStatus.BLOCKED, reduced.strategicPlans().objectives().get(task.objectiveId()).status());
    }

    @Test
    void productionRefusesToStartWhenEveryCrafterIsStarvingAndRecoversAfterOneExactRation() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:starving-crafter"), 91L));
        Settlement settlement = initial.bootstrap().settlements().getFirst(); HumanPopulation population = initial.humanPopulation();
        List<ResidentProfile> crafters = population.residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id())
                && resident.role() == ResidentRole.CRAFTER).toList();
        for (int cycle = 1; cycle <= ResidentNutrition.STARVING_AFTER_MISSED_CYCLES; cycle++) {
            for (ResidentProfile crafter : crafters) population = population.resolveNutrition(crafter.id(), cycle, false);
        }
        FrontierWorldState starving = productionTask(initial.withHumanPopulation(population), StrategicTaskStatus.PENDING);
        StrategicTask task = starving.strategicPlans().tasks().values().iterator().next();

        List<ProposedEvent> planned = FrontierWorldRuntimeDefinition.planScheduled(starving, ProductionProcess.start(task, 200L));
        ProductionBlocked block = planned.stream().map(ProposedEvent::payload).filter(ProductionBlocked.class::isInstance)
                .map(ProductionBlocked.class::cast).findFirst().orElseThrow();
        assertEquals(ProductionBlockReason.WORKER_UNAVAILABLE, block.reason());
        assertEquals(block, FrontierWorldRuntimeDefinition.payloadCodecs().decode(block.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(block)));
        FrontierWorldState reduced = ProductionProcess.reduceBlocked(starving, settlement.id(), block);
        assertEquals(starving, reduced);

        for (ResidentProfile crafter : crafters) population = population.resolveNutrition(crafter.id(), ResidentNutrition.STARVING_AFTER_MISSED_CYCLES + 1, true);
        FrontierWorldState recovered = productionTask(initial.withHumanPopulation(population), StrategicTaskStatus.PENDING);
        List<ProposedEvent> retried = FrontierWorldRuntimeDefinition.planScheduled(recovered, ProductionProcess.start(task, 300L));
        assertTrue(retried.stream().map(ProposedEvent::payload).anyMatch(ProductionStarted.class::isInstance));
    }

    @Test
    void unaffordableCompanyWorkBlocksBeforeItOccupiesTheWorkshop() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:production-finance"), 91L));
        SubjectId settlementId = new SubjectId("settlement:1");
        for (ProposedEvent event : CompanyFoundationProcess.plan(initial, CompanyFoundationProcess.review(settlementId, 1, 4_000L))) {
            if (event.payload() instanceof CompanyRegistered registered) initial = CompanyFoundationProcess.reduce(initial, settlementId, registered);
            if (event.payload() instanceof EmploymentContractOpened opened) initial = CompanyFoundationProcess.reduceEmployment(initial, settlementId, opened);
        }
        EconomicAccount treasury = initial.inventory().economics().require(settlementId);
        var accounts = new LinkedHashMap<>(initial.inventory().economics().accounts());
        accounts.put(settlementId, new EconomicAccount(settlementId, treasury.ownerKind(), treasury.status(), FixedScalar.ZERO, treasury.creditLimit()));
        FrontierWorldState state = productionTask(initial.withInventory(initial.inventory().withEconomics(new EconomicLedger(accounts))), StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();

        List<ProposedEvent> planned = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L));

        ProductionBlocked block = assertInstanceOf(ProductionBlocked.class, planned.getFirst().payload());
        assertEquals(ProductionBlockReason.FINANCE_UNAVAILABLE, block.reason());
        assertTrue(planned.stream().map(ProposedEvent::payload).noneMatch(ProductionStarted.class::isInstance));
        assertEquals(state, ProductionProcess.reduceBlocked(state, settlementId, block));
    }

    @Test
    void staleMarketQuoteDefersInsteadOfAdmittingAJobWithDifferentExactEmploymentTerms() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:market-stale-quote"), 91L));
        SubjectId settlement = new SubjectId("settlement:1");
        for (ProposedEvent event : CompanyFoundationProcess.plan(initial, CompanyFoundationProcess.review(settlement, 1, 4_000L))) {
            if (event.payload() instanceof CompanyRegistered registered) initial = CompanyFoundationProcess.reduce(initial, settlement, registered);
            if (event.payload() instanceof EmploymentContractOpened opened) initial = CompanyFoundationProcess.reduceEmployment(initial, settlement, opened);
        }
        FrontierWorldState state = productionTask(initial, StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        MarketDemand demand = MarketClearingProcess.foodDemand(state, task, 100L);
        SubjectId company = CompanyFoundationProcess.companyId(settlement);
        CompanyQuote stale = new CompanyQuote(new SubjectId("quote:test-stale-worker-terms"), demand.id(), company, demand.itemCount(),
                FixedScalar.ONE, 100L, demand.expiresAtTick());
        state = state.withCompanies(state.companies().withMarket(MarketOrderBook.empty().open(demand).publish(stale, 100L)));

        List<ProposedEvent> planned = MarketClearingProcess.plan(state, MarketClearingProcess.clear(demand, 1, 200L));

        assertEquals(1, planned.size());
        ScheduleEffect.Created retry = assertInstanceOf(ScheduleEffect.Created.class, planned.getFirst().payload());
        assertEquals("frontier.market.clear", retry.action().kind());
        assertEquals(200L + state.bootstrap().ruleset().cadence().marketRetryInterval(), retry.action().dueAt().ticks());
        assertTrue(planned.stream().map(ProposedEvent::payload).noneMatch(ProductionStarted.class::isInstance));
        assertTrue(planned.stream().map(ProposedEvent::payload).noneMatch(MarketWorkOrderAccepted.class::isInstance));
    }

    private static FrontierWorldState productionTask(FrontierWorldState state, StrategicTaskStatus status) {
        SubjectId settlement = new SubjectId("settlement:1");
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:test-production"), settlement,
                StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:test-production"), objective.id(), settlement, StrategicTaskKind.PRODUCE_BREAD,
                Optional.empty(), List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT), List.of(), status);
        return state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
    }

    private static PreparedProduction activePhysicalProduction() {
        MaterializedProduction materialized = activeMaterializedProduction();
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:production-transform-1-physical"), PhysicalIntentKind.PRODUCTION_TRANSFORMATION,
                PhysicalIntentStatus.PREPARED, materialized.job().id(), List.of(materialized.job().id(), materialized.job().consumedItemId(), materialized.job().outputItemId()),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED);
        return new PreparedProduction(materialized.state().preparePhysicalIntent(intent), materialized.job(), intent, materialized.order(), materialized.settlementId(), materialized.taskId());
    }

    private static MaterializedProduction activeMaterializedProduction() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:production-physical"), 91L));
        SubjectId settlement = new SubjectId("settlement:1"), depot = new SubjectId("container:1-depot");
        for (ProposedEvent event : CompanyFoundationProcess.plan(initial, CompanyFoundationProcess.review(settlement, 1, 4_000L))) {
            if (event.payload() instanceof CompanyRegistered registered) initial = CompanyFoundationProcess.reduce(initial, settlement, registered);
            if (event.payload() instanceof EmploymentContractOpened opened) initial = CompanyFoundationProcess.reduceEmployment(initial, settlement, opened);
        }
        SubjectId worker = initial.companies().companies().get(CompanyFoundationProcess.companyId(settlement)).founderId();
        FrontierWorldState state = productionTask(initial.withInventory(initial.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)), StrategicTaskStatus.ACTIVE);
        ProductionJob job = new ProductionJob(new SubjectId("job:production-1-physical"), settlement, new SubjectId("structure:1-workshop"),
                worker, new SubjectId("item:bootstrap-1-wheat"), new SubjectId("item:production-1-physical-bread"), "minecraft:bread", 64);
        state = CompanyWorkPaymentProcess.reserve(state.withProductionJob(job), job);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next(); EmploymentContract contract = CompanyWorkPaymentProcess.contractFor(state, job).orElseThrow();
        FinancialReservation reservation = CompanyWorkPaymentProcess.reservation(job, contract);
        MarketDemand demand = new MarketDemand(new SubjectId("demand:production-1-physical"), settlement, task.id(), "minecraft:bread", 64,
                FixedScalar.whole(2L), 0L, 1_000L, MarketDemandStatus.OPEN);
        CompanyQuote quote = new CompanyQuote(new SubjectId("quote:production-1-physical"), demand.id(), contract.companyId(), 64, contract.invoicePerCompletedJob(), 0L, 1_000L);
        MarketWorkOrder order = new MarketWorkOrder(new SubjectId("order:production-1-physical"), demand.id(), quote.id(), contract.companyId(), task.id(), job.id(),
                reservation.id(), quote.totalPrice(), MarketWorkOrderStatus.ACCEPTED);
        state = state.withCompanies(state.companies().withMarket(MarketOrderBook.empty().open(demand).publish(quote, 0L).accept(order, 0L)));
        return new MaterializedProduction(state, job, order, settlement, task.id());
    }

    private static ColdMarketJob coldMarketJob() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:production-cold-cancel"), 91L));
        SubjectId settlement = new SubjectId("settlement:1");
        for (ProposedEvent event : CompanyFoundationProcess.plan(state, CompanyFoundationProcess.review(settlement, 1, 4_000L))) {
            if (event.payload() instanceof CompanyRegistered registered) state = CompanyFoundationProcess.reduce(state, settlement, registered);
            if (event.payload() instanceof EmploymentContractOpened opened) state = CompanyFoundationProcess.reduceEmployment(state, settlement, opened);
        }
        state = productionTask(state, StrategicTaskStatus.ACTIVE);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ExactItemStack input = state.inventory().items().get(new SubjectId("item:bootstrap-1-wheat"));
        SubjectId company = CompanyFoundationProcess.companyId(settlement);
        SubjectId worker = state.companies().companies().get(company).founderId();
        ProductionJob job = new ProductionJob(new SubjectId("job:production-1-cold-cancel"), settlement, new SubjectId("structure:1-workshop"), worker,
                input.id(), new ProductionInputHold.Cold(input), new SubjectId("item:production-1-cold-cancel-bread"), "minecraft:bread", input.count());
        state = CompanyWorkPaymentProcess.reserve(state.startProductionJob(job, input.id()), job);
        EmploymentContract contract = CompanyWorkPaymentProcess.contractFor(state, job).orElseThrow();
        FinancialReservation reservation = CompanyWorkPaymentProcess.reservation(job, contract);
        MarketDemand demand = new MarketDemand(new SubjectId("demand:production-1-cold-cancel"), settlement, task.id(), "minecraft:bread", input.count(),
                FixedScalar.whole(2L), 0L, 1_000L, MarketDemandStatus.OPEN);
        CompanyQuote quote = new CompanyQuote(new SubjectId("quote:production-1-cold-cancel"), demand.id(), company, input.count(), contract.invoicePerCompletedJob(), 0L, 1_000L);
        MarketWorkOrder order = new MarketWorkOrder(new SubjectId("order:production-1-cold-cancel"), demand.id(), quote.id(), company, task.id(), job.id(),
                reservation.id(), quote.totalPrice(), MarketWorkOrderStatus.ACCEPTED);
        MarketOrderBook market = MarketOrderBook.empty().open(demand).publish(quote, 0L).accept(order, 0L);
        return new ColdMarketJob(state.withCompanies(state.companies().withMarket(market)), settlement, job, input, order);
    }

    private static CommandResult submitTransition(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine, WorldId world,
                                                  String suffix, PhysicalIntentId intent, PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation) {
        CommandId command = new CommandId("command:production-" + suffix);
        var checkpoint = engine.checkpoint();
        return engine.submit(new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(command), new PhysicalIntentTransition(intent, status, observation)));
    }

    private record PreparedProduction(FrontierWorldState state, ProductionJob job, PhysicalIntent intent, MarketWorkOrder order, SubjectId settlementId, SubjectId taskId) { }
    private record MaterializedProduction(FrontierWorldState state, ProductionJob job, MarketWorkOrder order, SubjectId settlementId, SubjectId taskId) { }
    private record ColdMarketJob(FrontierWorldState state, SubjectId settlementId, ProductionJob job, ExactItemStack input, MarketWorkOrder order) { }
}
