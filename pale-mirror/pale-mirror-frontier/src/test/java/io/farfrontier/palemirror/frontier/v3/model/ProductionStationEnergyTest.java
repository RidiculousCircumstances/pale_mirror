package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionStationEnergyTest {
    private static final SubjectId FUEL = new SubjectId("lot:station-test-fuel");
    private static FrontierWorldState fuel(FrontierWorldState state, ProductionStationSpec station) {
        var owner = state.inventory().containers().get(station.containerId()).ownerId();
        var account = ReferenceContainerCustody.scopeId(station.containerId());
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(FUEL, owner, "minecraft:coal", 4,
                "fixture:finite-fuel", List.of()), new CustodyAccount(account, new ResourceCustody.Container(station.containerId()), Map.of(FUEL, 4), Map.of()));
        return state.withInventory(state.inventory().withFungibleResources(ledger));
    }
    @Test void acquiredContainerWithoutMaterialBindingsWaitsBeforeBurningRealFuel() {
        var genesis = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:station-fuel-reacquisition"), 41,
                FrontierRulesets.fuelledQuarryGraybox()));
        var station = genesis.inventory().containers().values().stream().flatMap(record -> record.productionStation().stream()).findFirst().orElseThrow();
        var state = ReferenceContainerCustodyFixtures.observedAndHeld(fuel(genesis, station), station.containerId());
        assertTrue(ReferenceContainerCustody.hasOperationalCustody(state, station.containerId()));
        assertTrue(ProductionStationEnergy.next(state, station.id()).isEmpty(), "custody acquisition is not material binding");
        assertEquals(0, state.productionStations().require(station.id()).storedHeat());
        var account = ReferenceContainerCustody.scopeId(station.containerId());
        var ledger = state.inventory().fungibleResources();
        var stacks = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(station.containerId(), station.fuelSlot())), "minecraft:coal", 4));
        var ready = state.withInventory(state.inventory().withFungibleResources(ledger.rebind(account, 1,
                FungiblePhysicalObservation.bind(ledger, account, 1, stacks))));
        assertEquals(1, ProductionStationEnergy.next(ready, station.id()).orElseThrow().authorityEpoch());
    }
    @Test void bakerWaitingForFuelDoesNotClaimTheStationServiceTurn() {
        var state = BakeryPartialBatchTest.fixture(64, FrontierRulesets.fuelledQuarryGraybox());
        var task = state.strategicPlans().tasks().values().iterator().next();
        var start = ProductionProcess.planStart(state, ProductionProcess.start(task, 200)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance).map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(), new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), start);
        var job = start.job();
        for (long due = 300, turns = 0; state.productionJobs().get(job.id()).bakeryWork().orElseThrow().phase()
                == BakeryWorkState.Phase.DEPOT_PICKUP && turns < 400; due += 20, turns++) {
            var current = state.productionJobs().get(job.id());
            var step = ProductionProcess.planCompletion(state, ProductionProcess.complete(current, due)).stream()
                    .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance).map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
        }
        var current = state.productionJobs().get(job.id());
        assertEquals(BakeryWorkState.Phase.STATION_LOAD, current.bakeryWork().orElseThrow().phase());
        var station = ProductionStationEnergy.record(state, current.bakeryWork().orElseThrow().stationId()).productionStation().orElseThrow();
        assertEquals(station.containerId(), ProductionServiceAccess.point(state, current));
        var assignment = HumanAssignmentProjection.compile(state).assignment(job.workerId());
        assertFalse(ActivityExecutionCapabilities.waitingForServiceResource(state, assignment),
                "missing station fuel cannot suspend departure from the unrelated source depot");
        assertFalse(ResidentActivityCoordinator.shouldYieldAtOwnerCheckpoint(state, job.workerId(), 1_000));
        var atMachine = state.withActorBody(job.workerId(), station.workerStation().standingBody());
        assertTrue(ActivityExecutionCapabilities.waitingForServiceResource(atMachine, assignment),
                "the same fuel wait must release the machine's own temporary service footprint");
        assertResourceWaitDoesNotRestartClearance(atMachine, current, station);
        assertTrue(new ProductionServiceAccess().demands(state, station.containerId()).isEmpty());
        assertFalse(ProductionServiceAccess.mayAdvance(state, current, station.workerStation()));
        state = fuel(state, station);
        for (int count = 0; count < 2; count++) state = ProductionStationEnergy.apply(state, station.id(),
                new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.COLD_CONSUMED, ProductionStationEnergy.next(state, station.id()).orElseThrow(), List.of()));
        assertFalse(ActivityExecutionCapabilities.waitingForServiceResource(state, HumanAssignmentProjection.compile(state).assignment(job.workerId())));
        assertEquals(1, new ProductionServiceAccess().demands(state, station.containerId()).size());
        assertTrue(ProductionServiceAccess.available(state, current));
    }
    private static void assertResourceWaitDoesNotRestartClearance(FrontierWorldState state, ProductionJob job,
                                                                  ProductionStationSpec station) {
        var actor = job.workerId();
        var declaration = ServiceBoundaryComposition.declaration(state, station.containerId());
        var point = ServiceBoundaryComposition.geometry(state, declaration.identity());
        var waiting = point.waitingSurfaces().stream().filter(surface -> point.boundary().cleared(surface.standingBody()))
                .findFirst().orElseThrow();
        state = state.withActorBody(actor, waiting.standingBody());
        var points = ServiceBoundaryComposition.geometries(state);
        var goal = ServiceAreaDestinations.select(points, actor, waiting,
                ServiceBoundaryComposition.knowledge(state, declaration.identity()).forActor(state, actor), Set.of()).orElseThrow();
        assertTrue(points.stream().noneMatch(candidate -> ServiceAreaDestinations.temporary(candidate, goal)),
                "the post-clearance fixture must actually be outside every temporary footprint");
        var execution = state.actorExecutions().next(actor,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.SERVICE_EXIT, actor);
        var order = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(actor, actor, 0, 1,
                List.of(goal), TraversalCapability.PEDESTRIAN,
                io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
        var movement = new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement(order, 10_000,
                new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.ServiceExit(declaration.identity()), execution);
        state = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, execution, 10_000).commit(state,
                FrontierWorldStateUpdate.begin().actorMovements(Map.of(actor, movement)));
        assertTrue(ActivityExecutionCapabilities.waitingForServiceResource(state, HumanAssignmentProjection.compile(state).assignment(actor)));
        var action = ResidentActivityProcess.review(actor, 10_001);
        var refusal = assertInstanceOf(ResidentActivityAdmission.Waiting.class, ResidentActivityProcess.admission(state, action));
        assertEquals(ResidentActivityAdmission.Reason.MOVEMENT_HANDOFF, refusal.reason(),
                "a blocked owner cannot repeatedly cancel and recreate its unfinished clearance");
        // Construct the separate valid post-clearance precondition. This is a
        // pure admission test, not a synthetic movement or native arrival proof.
        var resting = state.withActorBody(actor, goal.standingBody()).withChanges(FrontierWorldStateUpdate.begin()
                .actorMovements(Map.of()).actorExecutions(state.actorExecutions().finish(execution)));
        assertTrue(resting.actorExecutions().actors().get(actor).suspended().isPresent());
        assertSuspendedWorkCannotAdmitScene(resting, job, 10_002);
        assertTrue(ResidentMealOpportunity.candidate(resting, actor, 10_002).isEmpty());
        assertFalse(ActivityExecutionCapabilities.mayResumeWork(resting, HumanAssignmentProjection.compile(resting).assignment(actor)));
        assertTrue(ResidentActivityProcess.plan(resting, ResidentActivityProcess.review(actor, 10_002)).stream()
                .noneMatch(event -> event.payload() instanceof io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionResumed),
                "even an admitted ordinary review cannot resume resource-blocked work");
        assertTrue(ResidentActivityProcess.wakeDependencies(resting, actor).contains(station.containerId()),
                "fuel must wake the retained worker even after it leaves the station footprint");
        resting = fuel(resting, station);
        for (int count = 0; count < 2; count++) resting = ProductionStationEnergy.apply(resting, station.id(),
                new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.COLD_CONSUMED,
                        ProductionStationEnergy.next(resting, station.id()).orElseThrow(), List.of()));
        assertTrue(ResidentMealOpportunity.candidate(resting, actor, 10_003).isEmpty());
        assertInstanceOf(ResidentActivityAdmission.Ready.class, ResidentActivityProcess.admission(resting,
                ResidentActivityProcess.review(actor, 10_003)), "available retained work resumes even without food");
        assertEquals(1, ResidentActivityProcess.plan(resting, ResidentActivityProcess.review(actor, 10_003)).stream()
                .filter(event -> event.payload() instanceof io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionResumed).count(),
                "fuel releases the retained executable work exactly once");
        var retained = resting.actorExecutions().actors().get(actor).suspended().orElseThrow();
        var resumed = resting.actorExecutions().next(actor, retained.activityKind(), retained.activityOwnerId());
        var working = ActorExecutionComposition.LIFECYCLE.prepareResume(resting, retained, resumed, Optional.empty(), 10_004)
                .commit(resting, FrontierWorldStateUpdate.begin());
        assertTrue(FrontierProductionWorkSceneSupport.candidate(working, working.productionJobs().get(job.id())).isPresent(),
                "the actual owner resumption restores HOT eligibility without losing the job");
    }
    private static void assertSuspendedWorkCannotAdmitScene(FrontierWorldState paused, ProductionJob job, long tick) {
        var presence = paused.actorExecutions().next(job.workerId(),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE, job.workerId());
        var idle = ActorExecutionComposition.LIFECYCLE.prepareBegin(paused, presence, tick)
                .commit(paused, FrontierWorldStateUpdate.begin());
        for (var state : List.of(paused, idle)) {
            assertEquals(job, state.productionJobs().get(job.id()), "assignment/cargo survive suspension");
            assertTrue(FrontierProductionWorkSceneSupport.candidate(state, job).isEmpty(),
                    "a retained job cannot adopt the body from idle or vacant current execution");
            assertThrows(IllegalArgumentException.class, () -> ProductionJourneyKnowledge.atScopeAdmission(state, job),
                    "the reducer's strict ownership guard must remain");
            var instant = new SimInstant(tick);
            var body = state.actorLocations().get(job.workerId()).body();
            var leaseId = new SceneLeaseId("lease:paused-production");
            var lease = SceneLease.forCause(leaseId, state.bootstrap().worldId(), new ProductionWorkSceneCause(job.id()),
                    body.supportingSurface().support(), instant, 0L, SceneLeaseStatus.PREPARED,
                    List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), leaseId, job.workerId()))),
                    Set.of(), Optional.empty());
            var ambient = new AmbientActorLease(job.workerId(), body, instant, 1L, AmbientLeaseStatus.HOT, AmbientGoalKind.WORK, body);
            var presented = state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(job.workerId(), ambient)));
            var handoff = new ProductionWorkSceneLeaseHandoff(lease.withAmbientHandoff(Set.of(job.workerId())),
                    List.of(new SceneMemberPosition(job.workerId(), body, state.actorLocations().get(job.workerId()).condition().health())));
            assertThrows(IllegalArgumentException.class, () -> ProductionProcess.rebaseForAmbientHandoff(presented, job.settlementId(), handoff));
            for (var payload : List.of(new ProductionWorkSceneLeasePrepared(lease), handoff)) {
                var input = payload instanceof ProductionWorkSceneLeaseHandoff ? presented : state;
                var base = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
                var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(
                        new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(state.bootstrap().worldId(), input, instant,
                                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(),
                                base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
                var id = new CommandId("command:paused-production");
                var result = engine.submit(new FrontierCommand(1, id, state.bootstrap().worldId(), Revision.ZERO, instant,
                        io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
                var rejection = assertInstanceOf(CommandResult.Rejected.class, result);
                assertNotEquals(RejectionCode.INVARIANT_FAILURE, rejection.rejection().code());
                assertEquals(EngineStatus.Kind.ACTIVE, engine.status().kind(),
                        "normal stale scene admission rejects before an event can quarantine the world");
                assertEquals(Revision.ZERO, engine.checkpoint().revision());
            }
        }
    }
    @Test void fuelledBatchConsumesRealFuelAndIngredientsAndSurvivesEverySnapshotBoundary() {
        var state = BakeryPartialBatchTest.fixture(64, FrontierRulesets.fuelledQuarryGraybox());
        var task = state.strategicPlans().tasks().values().iterator().next();
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), task.ownerId());
        var station = ProductionStationSpec.grayboxBakery(home.structures().stream().filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow());
        assertFalse(ProductionStationProcessing.canStart(state, station.id(), BakeryRecipes.BREAD, 63));
        state = fuel(state, station);
        var codec = new FrontierWorldStateCodec();
        for (int burns = 0; burns < 2; burns++) {
            var burn = ProductionStationEnergy.next(state, station.id()).orElseThrow();
            var event = new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.COLD_CONSUMED, burn, List.of());
            state = ProductionStationEnergy.apply(state, station.id(), event);
            assertEquals(state, codec.decode(codec.encode(state)));
        }
        assertTrue(ProductionStationProcessing.canStart(state, station.id(), BakeryRecipes.BREAD, 63));
        var start = ProductionProcess.planStart(state, ProductionProcess.start(task, 200)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance).map(ProductionStarted.class::cast).findFirst().orElseThrow();
        var job = start.job(); assertEquals(63, job.inputCount()); assertEquals(21, job.outputCount());
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(), new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), start);
        for (long due = 300, turns = 0; state.productionJobs().containsKey(job.id()) && turns < 700; due += 20, turns++) {
            due = BakeryTestClock.due(state, job, due);
            var current = state.productionJobs().get(job.id());
            if (current.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.STATION_UNLOAD)
                assertPlayerOutputLossClosesOnlyThisOrder(state, job, station);
            var step = ProductionProcess.planCompletion(state, ProductionProcess.complete(current, due)).stream()
                    .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance).map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
            state = codec.decode(codec.encode(state));
        }
        assertFalse(state.productionJobs().containsKey(job.id()));
        assertEquals(1520, state.productionStations().require(station.id()).storedHeat());
        assertEquals(2, state.inventory().fungibleResources().accounts().get(ReferenceContainerCustody.scopeId(station.containerId())).lotQuantities().get(FUEL));
        assertEquals(21, state.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:bread"));
        assertEquals(1, state.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:wheat"));
    }
    private static void assertPlayerOutputLossClosesOnlyThisOrder(FrontierWorldState before, ProductionJob job, ProductionStationSpec station) {
        var account = ReferenceContainerCustody.scopeId(station.containerId()); var ledger = before.inventory().fungibleResources();
        var fuel = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(station.containerId(), station.fuelSlot())), "minecraft:coal", 2);
        var product = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot())), "minecraft:bread", 21);
        var physical = ReferenceContainerCustodyFixtures.observedAndHeld(before.withInventory(before.inventory().withFungibleResources(
                ledger.rebind(account, 1, FungiblePhysicalObservation.bind(ledger, account, 1, List.of(fuel, product))))), station.containerId());
        var operation = BakeryStationOperation.require(physical, job);
        assertTrue(SourceStockClaimLoss.canDepart(physical, account, "minecraft:bread", 3));
        var remaining = List.of(fuel, new FungiblePhysicalObservation.Stack(product.address(), product.itemKind(), 18));
        var loss = new FungibleStockDepartureObserved(account, station.containerId(), job.settlementId(), 1,
                UUID.fromString("00000000-0000-0000-0000-000000000321"), UUID.randomUUID(), Map.of(job.outputItemId(), 3),
                Set.of(operation.outputClaimId()), remaining);
        var lost = SourceStockClaimLoss.settle(physical, loss);
        assertFalse(lost.productionJobs().containsKey(job.id()));
        assertTrue(lost.productionStations().require(station.id()).operation().isEmpty());
        assertEquals(physical.actorLocations().get(job.workerId()), lost.actorLocations().get(job.workerId()));
        assertEquals(18, lost.inventory().fungibleResources().lots().get(job.outputItemId()).quantity());
        assertEquals(1520, lost.productionStations().require(station.id()).storedHeat());
        var codec = new FrontierWorldStateCodec(); assertEquals(lost, codec.decode(codec.encode(lost)));
    }
    @Test void preparedHotBurnRecoversAndRejectsMissingDecrementUnrelatedMutationAndReplay() {
        var genesis = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hot-station-fuel"), 41, FrontierRulesets.fuelledQuarryGraybox()));
        var station = genesis.inventory().containers().values().stream().flatMap(record -> record.productionStation().stream()).findFirst().orElseThrow();
        var state = fuel(genesis, station);
        var account = ReferenceContainerCustody.scopeId(station.containerId());
        var address = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(station.containerId(), station.fuelSlot()));
        var ledger = state.inventory().fungibleResources();
        state = state.withInventory(state.inventory().withFungibleResources(ledger.rebind(account, 1,
                FungiblePhysicalObservation.bind(ledger, account, 1, List.of(new FungiblePhysicalObservation.Stack(address, "minecraft:coal", 4))))));
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state, station.containerId());
        var burn = ProductionStationEnergy.next(state, station.id()).orElseThrow();
        var prepared = new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.HOT_PREPARED, burn, List.of());
        var wal = FrontierWorldPayloadCodecs.create();
        assertEquals(prepared, wal.decode(prepared.type(), wal.encode(prepared)));
        state = ProductionStationEnergy.apply(state, station.id(), prepared);
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        var pending = state;
        assertEquals(0, pending.productionStations().require(station.id()).storedHeat());
        assertThrows(IllegalArgumentException.class, () -> ProductionStationEnergy.apply(pending, station.id(),
                new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.HOT_CONSUMED, burn, List.of(new FungiblePhysicalObservation.Stack(address, "minecraft:coal", 4)))));
        assertThrows(IllegalArgumentException.class, () -> ProductionStationEnergy.apply(pending, station.id(),
                new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.HOT_CONSUMED, burn, List.of(new FungiblePhysicalObservation.Stack(address, "minecraft:coal", 2)))));
        var received = new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.HOT_CONSUMED, burn, List.of(new FungiblePhysicalObservation.Stack(address, "minecraft:coal", 3)));
        var settled = ProductionStationEnergy.apply(pending, station.id(), received);
        assertEquals(1600, settled.productionStations().require(station.id()).storedHeat());
        assertThrows(IllegalArgumentException.class, () -> ProductionStationEnergy.apply(settled, station.id(), received));
        assertEquals(settled, codec.decode(codec.encode(settled)));
    }
}
