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
    void workProgressSurvivesSnapshotAndStartedPayloadWithoutUsingEnumOrder() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ProductionJob working = prepared.job().withWorkProgress(ProductionWorkProgress.processing(37));
        FrontierWorldState workingState = prepared.state().withChanges(FrontierWorldStateUpdate.begin()
                .productionJobs(java.util.Map.of(working.id(), working)));
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(workingState));
        assertEquals(ProductionWorkProgress.processing(37), restored.productionJobs().get(working.id()).workProgress());
        assertEquals(working.workTraversal(), restored.productionJobs().get(working.id()).workTraversal());
        assertEquals(working.traversalCursor(), restored.productionJobs().get(working.id()).traversalCursor());

        ProductionStarted started = new ProductionStarted(working, working.consumedItemId());
        ProductionStarted decoded = (ProductionStarted) FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(started));
        assertEquals(ProductionWorkProgress.processing(37), decoded.job().workProgress());
    }

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
    void materializedTransformationCannotBePreparedBeforeTheExactWorkerFinishesItsRetainedCycle() {
        MaterializedProduction prepared = activeMaterializedProduction();
        PhysicalIntent early = new PhysicalIntent(new PhysicalIntentId("intent:production-transform-too-early"), PhysicalIntentKind.PRODUCTION_TRANSFORMATION,
                PhysicalIntentStatus.PREPARED, prepared.job().id(), List.of(prepared.job().id(), prepared.job().consumedItemId(), prepared.job().outputItemId()),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.PRODUCTION_TRANSFORMED_OBSERVED);
        assertThrows(IllegalArgumentException.class, () -> prepared.state().preparePhysicalIntent(early));
    }

    @Test
    void blockedWorkshopWorkRetiresOnlyAfterTheExactWorkerSceneCloses() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ActorLocation worker = prepared.state().actorLocations().get(prepared.job().workerId());
        SubjectId settlementId = prepared.settlementId(), workshopId = prepared.job().facilityId();
        SettlementStructure workshop = prepared.state().bootstrap().settlements().stream().filter(value -> value.id().equals(settlementId))
                .findFirst().orElseThrow().structures().stream().filter(value -> value.id().equals(workshopId)).findFirst().orElseThrow();
        ProductionJob workJob = prepared.job().withWorkTraversal(ProductionWorkTraversal.compile(prepared.state().bootstrap(), workshop, worker, prepared.job().id()), 0);
        prepared = new MaterializedProduction(prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(workJob.id(), workJob))),
                workJob, prepared.order(), prepared.settlementId(), prepared.taskId());
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-finalize-test");
        SceneLease lease = SceneLease.forCause(leaseId, prepared.state().bootstrap().worldId(), new ProductionWorkSceneCause(prepared.job().id()),
                worker.supportingSurface().support(), new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(prepared.job().workerId(), SceneLease.deterministicEntityId(prepared.state().bootstrap().worldId(), prepared.job().workerId()))),
                java.util.Map.of(prepared.job().workerId(), worker.body()), java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = prepared.state().prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        int nextCursor = 1;
        BodyPosition nextStation = workJob.workTraversal().linearCorridorSurfaces().get(nextCursor).standingBody();
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceWorkTraversalAdvanced(hot, settlementId,
                new ProductionWorkTraversalAdvanced(workJob.id(), leaseId, worker.body(), nextCursor)));
        FrontierWorldState advanced = ProductionProcess.reduceWorkTraversalAdvanced(hot, prepared.settlementId(),
                new ProductionWorkTraversalAdvanced(workJob.id(), leaseId, nextStation, nextCursor));
        assertEquals(nextCursor, advanced.productionJobs().get(workJob.id()).traversalCursor());
        assertEquals(nextStation, advanced.sceneLeases().get(leaseId).memberPosition(workJob.workerId()),
                "one observed arrival must atomically advance both the work cursor and persisted HOT-body position");
        FrontierWorldState recoveredAdvance = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(advanced));
        assertEquals(nextStation, recoveredAdvance.sceneLeases().get(leaseId).memberPosition(workJob.workerId()),
                "restart recovery must retain the current work station rather than the initial workshop approach");
        byte[] preAtomicCursorSchema = new FrontierWorldStateCodec().encode(advanced);
        preAtomicCursorSchema[4] = 118;
        assertThrows(IllegalArgumentException.class, () -> new FrontierWorldStateCodec().decode(preAtomicCursorSchema),
                "the former schema must fail closed because its HOT lease did not retain the current worker cursor");
        SceneLease staleLease = advanced.sceneLeases().get(leaseId).withMemberPositions(java.util.Map.of(workJob.workerId(), worker.body()));
        FrontierWorldState staleCursor = advanced.withChanges(FrontierWorldStateUpdate.begin()
                .sceneLeases(java.util.Map.of(leaseId, staleLease)));
        int cursorAfterNext = nextCursor + 1;
        BodyPosition stationAfterNext = workJob.workTraversal().linearCorridorSurfaces().get(cursorAfterNext).standingBody();
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceWorkTraversalAdvanced(staleCursor, settlementId,
                        new ProductionWorkTraversalAdvanced(workJob.id(), leaseId, stationAfterNext, cursorAfterNext)),
                "a malformed recovered HOT lease must fail closed rather than advance a split worker cursor");
        FrontierWorldState blocked = hot.withStrategicPlans(hot.strategicPlans().transitionTask(prepared.taskId(), StrategicTaskStatus.BLOCKED))
                .transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        FrontierWorldState closed = blocked.releaseSceneLease(leaseId, List.of(new SceneMemberPosition(prepared.job().workerId(), worker.body(), worker.condition().health())));
        assertTrue(closed.productionJobs().containsKey(prepared.job().id()), "closed lease remains the durable hand-off before job retirement");
        FrontierWorldState finalized = ProductionProcess.reduceWorkSceneFinalized(closed, prepared.settlementId(), new ProductionWorkSceneFinalized(leaseId, prepared.job().id()));
        assertFalse(finalized.productionJobs().containsKey(prepared.job().id()));
        assertEquals(MarketWorkOrderStatus.CANCELLED, finalized.companies().market().workOrders().get(prepared.order().id()).status());
        assertFalse(finalized.inventory().economics().reservations().containsKey(prepared.order().reservationId()));
    }

    @Test
    void blockedRetainedWorkEdgeDrainsOnlyThatExactHotWorkerAndRejectsForgedCursorEvidence() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ActorLocation worker = prepared.state().actorLocations().get(prepared.job().workerId());
        SettlementStructure workshop = prepared.state().bootstrap().settlements().stream().filter(value -> value.id().equals(prepared.settlementId()))
                .findFirst().orElseThrow().structures().stream().filter(value -> value.id().equals(prepared.job().facilityId())).findFirst().orElseThrow();
        ProductionJob job = prepared.job().withWorkTraversal(ProductionWorkTraversal.compile(prepared.state().bootstrap(), workshop, worker, prepared.job().id()), 0);
        FrontierWorldState withWork = prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(job.id(), job)));
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-route-blocked-hot");
        SceneLease lease = SceneLease.forCause(leaseId, withWork.bootstrap().worldId(), new ProductionWorkSceneCause(job.id()), worker.supportingSurface().support(),
                new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED, List.of(new SceneMember(job.workerId(),
                SceneLease.deterministicEntityId(withWork.bootstrap().worldId(), job.workerId()))), java.util.Map.of(job.workerId(), worker.body()), java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = withWork.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        WorldId world = new WorldId("frontier:production-route-blocked");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, hot, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        ProductionWorkTraversalBlocked blocked = new ProductionWorkTraversalBlocked(job.id(), leaseId, worker.body(), 1);
        var checkpoint = engine.checkpoint(); CommandId command = new CommandId("command:production-route-blocked");

        CommandResult result = engine.submit(new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), blocked));
        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
        FrontierWorldState draining = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(SceneLeaseStatus.DRAINING, draining.sceneLeases().get(leaseId).status());
        assertEquals(StrategicTaskStatus.BLOCKED, draining.strategicPlans().tasks().get(prepared.taskId()).status());
        assertTrue(draining.productionJobs().containsKey(job.id()), "the job must wait for the exact physical worker release");
        assertEquals(blocked, FrontierWorldRuntimeDefinition.payloadCodecs().decode(blocked.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(blocked)));

        FrontierWorldState closed = draining.releaseSceneLease(leaseId, List.of(new SceneMemberPosition(job.workerId(), worker.body(), worker.condition().health())));
        FrontierWorldState cancelled = ProductionProcess.reduceWorkSceneFinalized(closed, prepared.settlementId(), new ProductionWorkSceneFinalized(leaseId, job.id()));
        assertFalse(cancelled.productionJobs().containsKey(job.id()));
        assertEquals(MarketWorkOrderStatus.CANCELLED, cancelled.companies().market().workOrders().get(prepared.order().id()).status());

        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceWorkTraversalBlocked(hot, prepared.settlementId(),
                new ProductionWorkTraversalBlocked(job.id(), leaseId, worker.body(), 2)), "an executor cannot skip an immutable edge when reporting a block");
    }

    @Test
    void conflictedProductionWorkLeaseKeepsItsExactWorkerReservedUntilExplicitRecovery() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ActorLocation worker = prepared.state().actorLocations().get(prepared.job().workerId());
        SettlementStructure workshop = prepared.state().bootstrap().settlements().stream()
                .filter(value -> value.id().equals(prepared.settlementId())).findFirst().orElseThrow().structures().stream()
                .filter(value -> value.id().equals(prepared.job().facilityId())).findFirst().orElseThrow();
        ProductionJob workJob = prepared.job().withWorkTraversal(ProductionWorkTraversal.compile(prepared.state().bootstrap(), workshop, worker, prepared.job().id()), 0);
        FrontierWorldState withWork = prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(workJob.id(), workJob)));
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-conflict-reservation");
        SceneLease lease = SceneLease.forCause(leaseId, withWork.bootstrap().worldId(), new ProductionWorkSceneCause(workJob.id()),
                worker.supportingSurface().support(), new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(workJob.workerId(), SceneLease.deterministicEntityId(withWork.bootstrap().worldId(), workJob.workerId()))),
                java.util.Map.of(workJob.workerId(), worker.body()), java.util.Set.of(), Optional.empty());
        FrontierWorldState conflicted = withWork.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.CONFLICT);

        assertTrue(FrontierProductionWorkSceneSupport.nextCandidate(conflicted).isEmpty(),
                "a visible conflict retains the same exact worker instead of admitting a second scene lease");
    }

    @Test
    void ambientHandoffRebasesOnlyAnUnstartedWorkshopTraversalToTheObservedWorkerBody() {
        FrontierWorldState state = FrontierDevelopmentScenarios.materializedProductionInputTheftFixture(
                new WorldId("frontier:production-observed-handoff"), 41L).state();
        ProductionJob job = state.productionJobs().get(new SubjectId("job:production-development-input-theft"));
        SurfaceAnchor observedSurface = job.workTraversal().linearCorridorSurfaces().get(1);
        BodyPosition observedBody = observedSurface.standingBody();
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-observed-handoff");
        SceneLease lease = SceneLease.forCause(leaseId, state.bootstrap().worldId(), new ProductionWorkSceneCause(job.id()),
                observedSurface.support(), new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), leaseId, job.workerId()))),
                java.util.Map.of(job.workerId(), observedBody), java.util.Set.of(job.workerId()), Optional.empty());
        ProductionWorkSceneLeaseHandoff handoff = new ProductionWorkSceneLeaseHandoff(lease,
                List.of(new SceneMemberPosition(job.workerId(), observedBody, state.actorLocations().get(job.workerId()).condition().health())));

        FrontierWorldState rebased = ProductionProcess.rebaseForAmbientHandoff(state, job.settlementId(), handoff);
        ProductionJob accepted = rebased.productionJobs().get(job.id());
        assertEquals(observedSurface, accepted.workTraversal().linearCorridorSurfaces().getFirst());
        assertEquals(0, accepted.traversalCursor());
        assertEquals(accepted, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(rebased)).productionJobs().get(job.id()),
                "restart retains the observed worker as the origin of its unstarted HOT route");

        ProductionJob advanced = accepted.withWorkTraversal(accepted.workTraversal(), 1);
        FrontierWorldState afterProgress = rebased.withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(advanced.id(), advanced)));
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.rebaseForAmbientHandoff(afterProgress, job.settlementId(), handoff),
                "a later hand-off may not erase retained workshop progress");
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
    void productionStartRetainsTheExactCrafterToWorkshopPortTopology() {
        FrontierWorldState state = productionTask(FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:production-route"), 91L)),
                StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionJob job = ProductionProcess.planStart(state, ProductionProcess.start(task, 100L)).stream().map(ProposedEvent::payload)
                .filter(ProductionStarted.class::isInstance).map(ProductionStarted.class::cast).findFirst().orElseThrow().job();
        SettlementStructure workshop = state.bootstrap().settlements().stream().filter(settlement -> settlement.id().equals(job.settlementId()))
                .findFirst().orElseThrow().structures().stream().filter(structure -> structure.id().equals(job.facilityId())).findFirst().orElseThrow();
        SettlementWorkshopServicePort port = SettlementWorkshopServicePort.forWorkshop(workshop);
        java.util.List<SurfaceAnchor> corridor = job.workTraversal().linearCorridorSurfaces();
        assertEquals(state.actorLocations().get(job.workerId()).supportingSurface(), corridor.getFirst());
        assertEquals(port.inputStation(), corridor.get(corridor.size() - 2));
        assertEquals(port.workStation(), corridor.getLast());
        assertEquals(0, job.traversalCursor());
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
        BodyPosition position = prepared.state().actorLocations().get(prepared.job().workerId()).body();
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
        BodyPosition position = prepared.state().actorLocations().get(prepared.job().workerId()).body();
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
        BodyPosition position = prepared.state().actorLocations().get(prepared.job().workerId()).body();
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
        ProductionJob job = state.productionJobs().get(new SubjectId("job:production-development-input-theft"));
        SubjectId worker = job.workerId();
        SettlementStructure workshop = FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId()).structures().stream()
                .filter(structure -> structure.id().equals(job.facilityId())).findFirst().orElseThrow();
        BlockPosition target = SettlementWorkshopServicePort.forWorkshop(workshop).exteriorApproach().support();

        assertEquals(new SubjectId("resident:1-15"), worker);
        assertEquals(target, FrontierTestPositions.supportOf(state.actorLocations().get(worker)));
        assertTrue(state.actorLocations().entrySet().stream().filter(entry -> !entry.getKey().equals(worker))
                .noneMatch(entry -> Math.max(Math.abs(FrontierTestPositions.supportOf(entry.getValue()).x() - target.x()),
                        Math.abs(FrontierTestPositions.supportOf(entry.getValue()).z() - target.z())) <= 8));
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
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout).findFirst().orElseThrow();
        state = state.withActorBody(scout.id(), FrontierTestPositions.bodyAboveSupport(settlement.anchor()));
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
                prepared.state().bootstrap().hive().bioforms().stream().filter(Bioform::isScout).findFirst().orElseThrow().id(),
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
    void playerTakingMaterializedInputDrainsTheExactHotWorkerBeforeRetiringTheMarketWork() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ActorLocation worker = prepared.state().actorLocations().get(prepared.job().workerId());
        SettlementStructure workshop = prepared.state().bootstrap().settlements().stream().filter(value -> value.id().equals(prepared.settlementId()))
                .findFirst().orElseThrow().structures().stream().filter(value -> value.id().equals(prepared.job().facilityId())).findFirst().orElseThrow();
        ProductionJob job = prepared.job().withWorkTraversal(ProductionWorkTraversal.compile(prepared.state().bootstrap(), workshop, worker, prepared.job().id()), 0);
        FrontierWorldState withWork = prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(job.id(), job)));
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-player-input-hot");
        SceneLease lease = SceneLease.forCause(leaseId, withWork.bootstrap().worldId(), new ProductionWorkSceneCause(job.id()), worker.supportingSurface().support(),
                new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED, List.of(new SceneMember(job.workerId(),
                SceneLease.deterministicEntityId(withWork.bootstrap().worldId(), job.workerId()))), java.util.Map.of(job.workerId(), worker.body()), java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = withWork.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        WorldId world = new WorldId("frontier:production-hot-player-input");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, hot, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        ExactItemStack input = hot.inventory().items().get(job.consumedItemId());
        InventoryCustody.Player player = new InventoryCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000066"));
        var checkpoint = engine.checkpoint();
        CommandResult result = engine.submit(new FrontierCommand(1, new CommandId("command:production-player-takes-hot-input"), world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(new CommandId("command:production-player-takes-hot-input")),
                new ExactItemCustodyChanged(input.id(), input.custody(), player)));

        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
        FrontierWorldState draining = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(draining.productionJobs().containsKey(job.id()), "the exact worker job remains until its body has released");
        assertEquals(player, draining.inventory().items().get(input.id()).custody());
        assertEquals(SceneLeaseStatus.DRAINING, draining.sceneLeases().get(leaseId).status());
        assertEquals(StrategicTaskStatus.BLOCKED, draining.strategicPlans().tasks().get(prepared.taskId()).status());
        assertEquals(MarketWorkOrderStatus.ACCEPTED, draining.companies().market().workOrders().get(prepared.order().id()).status());

        FrontierWorldState closed = draining.releaseSceneLease(leaseId, List.of(new SceneMemberPosition(job.workerId(), worker.body(), worker.condition().health())));
        FrontierWorldState cancelled = ProductionProcess.reduceWorkSceneFinalized(closed, prepared.settlementId(), new ProductionWorkSceneFinalized(leaseId, job.id()));
        assertFalse(cancelled.productionJobs().containsKey(job.id()));
        assertEquals(MarketWorkOrderStatus.CANCELLED, cancelled.companies().market().workOrders().get(prepared.order().id()).status());
        assertTrue(cancelled.inventory().economics().reservations().isEmpty());
    }

    @Test
    void physicalWorkshopLossBlocksTheSameHotJobBeforeItsWorkerRelease() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ActorLocation worker = prepared.state().actorLocations().get(prepared.job().workerId());
        SettlementStructure workshop = prepared.state().bootstrap().settlements().stream().filter(value -> value.id().equals(prepared.settlementId()))
                .findFirst().orElseThrow().structures().stream().filter(value -> value.id().equals(prepared.job().facilityId())).findFirst().orElseThrow();
        ProductionJob job = prepared.job().withWorkTraversal(ProductionWorkTraversal.compile(prepared.state().bootstrap(), workshop, worker, prepared.job().id()), 0);
        FrontierWorldState withWork = prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(job.id(), job)));
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-workshop-loss-hot");
        SceneLease lease = SceneLease.forCause(leaseId, withWork.bootstrap().worldId(), new ProductionWorkSceneCause(job.id()), worker.supportingSurface().support(),
                new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED, List.of(new SceneMember(job.workerId(),
                SceneLease.deterministicEntityId(withWork.bootstrap().worldId(), job.workerId()))), java.util.Map.of(job.workerId(), worker.body()), java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = withWork.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        BlockPosition lostStation = SettlementWorkshopServicePort.forWorkshop(workshop).workStation().support();
        FrontierWorldState damaged = hot.recordPhysicalDelta(new PhysicalDelta(lostStation, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(workshop.id()), Optional.of(GrayboxSemanticPart.WORKSHOP_PROCESS_STATION), "player:test-workshop-loss"));

        List<ProposedEvent> planned = ProductionProcess.planFacilityUnavailable(damaged, workshop.id());

        assertEquals(3, planned.size(), "physical facility loss must block, mark the task and drain the retained worker scene immediately");
        assertEquals(ProductionBlockReason.FACILITY_UNAVAILABLE,
                assertInstanceOf(ProductionBlocked.class, planned.getFirst().payload()).reason());
        assertEquals(StrategicTaskStatus.BLOCKED, assertInstanceOf(StrategicTaskTransition.class, planned.get(1).payload()).status());
        SceneLeaseTransition draining = assertInstanceOf(SceneLeaseTransition.class, planned.get(2).payload());
        assertEquals(leaseId, draining.leaseId());
        assertEquals(SceneLeaseStatus.DRAINING, draining.status());
    }

    @Test
    void observedWorkshopLossRoutesThroughThePhysicalPlannerAndDrainsTheHotWorker() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ActorLocation worker = prepared.state().actorLocations().get(prepared.job().workerId());
        SettlementStructure workshop = prepared.state().bootstrap().settlements().stream().filter(value -> value.id().equals(prepared.settlementId()))
                .findFirst().orElseThrow().structures().stream().filter(value -> value.id().equals(prepared.job().facilityId())).findFirst().orElseThrow();
        ProductionJob job = prepared.job().withWorkTraversal(ProductionWorkTraversal.compile(prepared.state().bootstrap(), workshop, worker, prepared.job().id()), 0);
        FrontierWorldState withWork = prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(job.id(), job)));
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-observed-workshop-loss-hot");
        SceneLease lease = SceneLease.forCause(leaseId, withWork.bootstrap().worldId(), new ProductionWorkSceneCause(job.id()), worker.supportingSurface().support(),
                new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED, List.of(new SceneMember(job.workerId(),
                SceneLease.deterministicEntityId(withWork.bootstrap().worldId(), job.workerId()))), java.util.Map.of(job.workerId(), worker.body()), java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = withWork.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        WorldId world = new WorldId("frontier:production-observed-workshop-loss");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, hot, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        BlockPosition station = SettlementWorkshopServicePort.forWorkshop(workshop).workStation().support();
        PhysicalDelta loss = new PhysicalDelta(station, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(workshop.id()),
                Optional.of(GrayboxSemanticPart.WORKSHOP_PROCESS_STATION), "player:test-observed-workshop-loss");
        var checkpoint = engine.checkpoint(); CommandId command = new CommandId("command:production-observed-workshop-loss");

        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalDeltaObserved(loss))));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(StructureCondition.DAMAGED, after.structureConditions().get(workshop.id()));
        assertEquals(StrategicTaskStatus.BLOCKED, after.strategicPlans().tasks().get(prepared.taskId()).status());
        assertTrue(after.productionJobs().containsKey(job.id()), "the job remains only until its exact body release is durable");
        assertEquals(SceneLeaseStatus.DRAINING, after.sceneLeases().get(leaseId).status());
    }

    @Test
    void playerTakingMaterializedInputAbortsPreparedWorkerSceneWithoutInventingABodyRelease() {
        MaterializedProduction prepared = activeMaterializedProduction();
        ActorLocation worker = prepared.state().actorLocations().get(prepared.job().workerId());
        SettlementStructure workshop = prepared.state().bootstrap().settlements().stream().filter(value -> value.id().equals(prepared.settlementId()))
                .findFirst().orElseThrow().structures().stream().filter(value -> value.id().equals(prepared.job().facilityId())).findFirst().orElseThrow();
        ProductionJob job = prepared.job().withWorkTraversal(ProductionWorkTraversal.compile(prepared.state().bootstrap(), workshop, worker, prepared.job().id()), 0);
        FrontierWorldState withWork = prepared.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(java.util.Map.of(job.id(), job)));
        var leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:production-player-input-prepared");
        SceneLease lease = SceneLease.forCause(leaseId, withWork.bootstrap().worldId(), new ProductionWorkSceneCause(job.id()), worker.supportingSurface().support(),
                new SimInstant(100L), 1L, SceneLeaseStatus.PREPARED, List.of(new SceneMember(job.workerId(),
                SceneLease.deterministicEntityId(withWork.bootstrap().worldId(), job.workerId()))), java.util.Map.of(job.workerId(), worker.body()), java.util.Set.of(), Optional.empty());
        FrontierWorldState scenePrepared = withWork.prepareSceneLease(lease);
        WorldId world = new WorldId("frontier:production-prepared-player-input");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, scenePrepared, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        ExactItemStack input = scenePrepared.inventory().items().get(job.consumedItemId());
        InventoryCustody.Player player = new InventoryCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000067"));
        var checkpoint = engine.checkpoint();
        CommandResult result = engine.submit(new FrontierCommand(1, new CommandId("command:production-player-takes-prepared-input"), world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(new CommandId("command:production-player-takes-prepared-input")),
                new ExactItemCustodyChanged(input.id(), input.custody(), player)));

        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
        FrontierWorldState cancelled = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertFalse(cancelled.productionJobs().containsKey(job.id()));
        assertEquals(SceneLeaseStatus.CLOSED, cancelled.sceneLeases().get(leaseId).status());
        assertEquals(player, cancelled.inventory().items().get(input.id()).custody());
        assertEquals(MarketWorkOrderStatus.CANCELLED, cancelled.companies().market().workOrders().get(prepared.order().id()).status());
        assertTrue(cancelled.inventory().economics().reservations().isEmpty());
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
    void unknownPhysicalProductionRetainsTheExactActiveJobWithoutDiscardingCanonicalClaim() {
        PreparedProduction prepared = activePhysicalProduction();

        FrontierWorldState blocked = prepared.state().transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());

        assertEquals(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, blocked.physicalIntents().get(prepared.intent().id()).status());
        assertTrue(blocked.inventory().items().containsKey(new SubjectId("item:bootstrap-1-wheat")));
        assertEquals(StrategicTaskStatus.ACTIVE, blocked.strategicPlans().tasks().values().stream()
                .filter(task -> task.kind() == StrategicTaskKind.PRODUCE_BREAD).findFirst().orElseThrow().status());
    }

    @Test
    void exactOutputObservedAfterRestartQuarantineCompletesTheSameRetainedProductionJob() {
        PreparedProduction prepared = activePhysicalProduction();
        FrontierWorldState unknown = prepared.state()
                .transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty())
                .transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        ExactItemStack input = unknown.inventory().items().get(prepared.job().consumedItemId());
        ProductionTransformationObservation receipt = new ProductionTransformationObservation(
                new PhysicalObservationId("observation:production-restart-output"), prepared.intent().id(), input.id(),
                prepared.job().outputItemId(), input.count(), prepared.job().outputCount());

        FrontierWorldState completed = unknown.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));

        assertTrue(completed.productionJobs().isEmpty());
        assertEquals("minecraft:bread", completed.inventory().items().get(prepared.job().outputItemId()).itemKind());
        assertEquals(StrategicTaskStatus.COMPLETED, completed.strategicPlans().tasks().values().stream()
                .filter(task -> task.kind() == StrategicTaskKind.PRODUCE_BREAD).findFirst().orElseThrow().status());
    }

    @Test
    void coldProductionBlocksItsDepotFromBecomingAPartialPhysicalSurface() {
        ColdMarketJob cold = coldMarketJob();
        SubjectId depot = FrontierWorldState.depotId(cold.settlementId());
        assertTrue(ContainerSurfaceActivationStateSupport.blockedByColdProduction(cold.state(), depot));
        assertFalse(ContainerSurfaceActivationStateSupport.blockedByColdProduction(cold.state(), new SubjectId("container:2-depot")));

        WorldId world = new WorldId("frontier:cold-production-surface");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, cold.state(), SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));

        assertInstanceOf(CommandResult.Accepted.class, submitSurface(engine, world, "surface-prepared", depot, ContainerSurfaceStatus.PREPARED));
        CommandResult rejected = submitSurface(engine, world, "surface-active", depot, ContainerSurfaceStatus.ACTIVE);
        assertInstanceOf(CommandResult.Rejected.class, rejected);
        assertEquals(ContainerSurfaceStatus.PREPARED, new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState())
                .inventory().surfaces().get(depot).status());
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
        ProductionJob ready = materialized.job().withWorkProgress(ProductionWorkProgress.outputReady());
        materialized = new MaterializedProduction(materialized.state().withChanges(FrontierWorldStateUpdate.begin()
                .productionJobs(java.util.Map.of(ready.id(), ready))), ready, materialized.order(), materialized.settlementId(), materialized.taskId());
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

    private static CommandResult submitSurface(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine, WorldId world,
                                               String suffix, SubjectId container, ContainerSurfaceStatus status) {
        var checkpoint = engine.checkpoint(); CommandId id = new CommandId("command:" + suffix);
        return engine.submit(new FrontierCommand(1, id, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), new ContainerSurfaceTransition(container, status)));
    }

    private record PreparedProduction(FrontierWorldState state, ProductionJob job, PhysicalIntent intent, MarketWorkOrder order, SubjectId settlementId, SubjectId taskId) { }
    private record MaterializedProduction(FrontierWorldState state, ProductionJob job, MarketWorkOrder order, SubjectId settlementId, SubjectId taskId) { }
    private record ColdMarketJob(FrontierWorldState state, SubjectId settlementId, ProductionJob job, ExactItemStack input, MarketWorkOrder order) { }
}
