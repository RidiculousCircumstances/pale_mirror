package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.SettlementServiceWorkProcess;
import io.farfrontier.palemirror.frontier.v3.process.PhysicalIntentLifecycleFixture;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Canonical admission regression for the first reusable settlement service-work form. */
class SettlementServiceWorkProcessTest {
    @Test
    void decontaminationAdmissionAtomicallyRetainsMedicStationsInputAndEndpoint() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:service-work"), 211L);
        Settlement settlement = settlement(bootstrap, "settlement:9");
        InfectionCell cell = treatmentCell(bootstrap, settlement);
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SubjectId item = new SubjectId("item:service-work-reagent");
        FrontierWorldState state = taskState(bootstrap, settlement, cell).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, settlement.id(), DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));

        List<ProposedEvent> planned = SettlementServiceWorkProcess.planDecontamination(state, SettlementServiceWorkProcess.scan(1, 1_000L));
        StrategicTaskTransition activation = planned.stream().map(ProposedEvent::payload).filter(StrategicTaskTransition.class::isInstance)
                .map(StrategicTaskTransition.class::cast).findFirst().orElseThrow();
        SettlementServiceWorkStarted started = planned.stream().map(ProposedEvent::payload).filter(SettlementServiceWorkStarted.class::isInstance)
                .map(SettlementServiceWorkStarted.class::cast).findFirst().orElseThrow();
        assertEquals(StrategicTaskStatus.ACTIVE, activation.status());
        assertEquals(started.work().id(), started.inputIssueIntent().causeSubjectId());
        assertEquals(started.work().id(), started.endpointIntent().roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.SETTLEMENT_SERVICE_WORK));
        assertEquals(started.work().workerId(), started.endpointIntent().roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.WORKER));
        assertEquals(item, started.endpointIntent().roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.INPUT_ITEM));
        assertFalse(planned.stream().anyMatch(event -> event.payload() instanceof io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared),
                "admission retains both boundaries atomically instead of exposing an old direct endpoint");

        FrontierWorldState active = state.withStrategicPlans(state.strategicPlans().transitionTask(activation.taskId(), activation.status()));
        FrontierWorldState admitted = SettlementServiceWorkProcess.reduceStarted(active, settlement.id(), started);
        SettlementServiceWork work = admitted.serviceWorks().get(started.work().id());
        assertEquals(started.work(), work);
        assertEquals(HumanAssignmentKind.SETTLEMENT_SERVICE, HumanAssignmentProjection.compile(admitted).assignment(work.workerId()).kind());
        assertEquals(work.inputIssueIntentId(), admitted.physicalIntents().get(work.inputIssueIntentId()).id());
        assertEquals(work.endpointIntentId(), admitted.physicalIntents().get(work.endpointIntentId()).id());
        SettlementServiceWorkStarted decoded = assertInstanceOf(SettlementServiceWorkStarted.class,
                FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(started)));
        assertEquals(started, decoded);
        assertEquals(started.execution(), SettlementServiceExecutionAuthority.current(admitted, work));
        assertThrows(IllegalArgumentException.class, () -> admitted.withChanges(FrontierWorldStateUpdate.begin()
                .actorExecutions(SettlementServiceExecutionAuthority.retired(admitted, work))),
                "retained active service cannot lose its exact worker authority");
        byte[] encoded = FrontierWorldRuntimeDefinition.payloadCodecs().encode(started);
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldRuntimeDefinition.payloadCodecs().decode(started.type(),
                java.util.Arrays.copyOf(encoded, encoded.length - 1)), "partial execution identity is not a legacy admission");
        assertEquals(admitted, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(admitted)));
    }

    @Test
    void missingExactDepotReagentBlocksInsteadOfCreatingServiceWork() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:service-work-blocked"), 212L);
        Settlement settlement = settlement(bootstrap, "settlement:9");
        FrontierWorldState state = taskState(bootstrap, settlement, treatmentCell(bootstrap, settlement));

        List<ProposedEvent> planned = SettlementServiceWorkProcess.planDecontamination(state, SettlementServiceWorkProcess.scan(1, 1_000L));

        assertTrue(planned.stream().map(ProposedEvent::payload).filter(StrategicTaskTransition.class::isInstance)
                .map(StrategicTaskTransition.class::cast).anyMatch(value -> value.status() == StrategicTaskStatus.BLOCKED));
        assertFalse(planned.stream().anyMatch(event -> event.payload() instanceof SettlementServiceWorkStarted));
    }

    @Test
    void forgedAdmissionCannotReplaceTheExactEndpointPlan() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:service-work-forgery"), 213L);
        Settlement settlement = settlement(bootstrap, "settlement:9");
        InfectionCell cell = treatmentCell(bootstrap, settlement);
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SubjectId item = new SubjectId("item:service-work-forgery-reagent");
        FrontierWorldState state = taskState(bootstrap, settlement, cell).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, settlement.id(), DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));
        SettlementServiceWorkStarted planned = SettlementServiceWorkProcess.planDecontamination(state, SettlementServiceWorkProcess.scan(1, 1_000L)).stream()
                .map(ProposedEvent::payload).filter(SettlementServiceWorkStarted.class::isInstance).map(SettlementServiceWorkStarted.class::cast)
                .findFirst().orElseThrow();
        FrontierWorldState active = state.withStrategicPlans(state.strategicPlans().transitionTask(planned.taskId(), StrategicTaskStatus.ACTIVE));
        var forgedEndpoint = new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent(planned.endpointIntent().id(), planned.endpointIntent().kind(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, planned.endpointIntent().causeSubjectId(),
                planned.endpointIntent().roles(), planned.endpointIntent().origin(), planned.endpointIntent().radiusBlocks(),
                planned.endpointIntent().postcondition(), planned.endpointIntent().lifecycleOwner());

        assertThrows(IllegalArgumentException.class, () -> SettlementServiceWorkProcess.reduceStarted(active, settlement.id(),
                new SettlementServiceWorkStarted(planned.taskId(), planned.work(), planned.inputIssueIntent(), forgedEndpoint, planned.execution())));
    }

    @Test
    void hotWorkerAdvanceMovesOnlyOneRetainedCursorAndLeaseCheckpoint() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:service-work-hot"), 214L);
        Settlement settlement = settlement(bootstrap, "settlement:9");
        InfectionCell cell = treatmentCell(bootstrap, settlement);
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SubjectId item = new SubjectId("item:service-work-hot-reagent");
        FrontierWorldState source = taskState(bootstrap, settlement, cell).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, settlement.id(), DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));
        SettlementServiceWorkStarted started = SettlementServiceWorkProcess.planDecontamination(source, SettlementServiceWorkProcess.scan(1, 1_000L)).stream()
                .map(ProposedEvent::payload).filter(SettlementServiceWorkStarted.class::isInstance).map(SettlementServiceWorkStarted.class::cast).findFirst().orElseThrow();
        FrontierWorldState active = source.withStrategicPlans(source.strategicPlans().transitionTask(started.taskId(), StrategicTaskStatus.ACTIVE));
        FrontierWorldState admitted = SettlementServiceWorkProcess.reduceStarted(active, settlement.id(), started);
        SettlementServiceWork work = admitted.serviceWorks().get(started.work().id());
        assertTrue(work.inputTraversal().linearCorridorSurfaces().size() > 1, "fixture must exercise a real retained source approach");
        SurfaceAnchor start = work.inputTraversal().linearCorridorSurfaces().getFirst();
        SceneLease lease = SceneLease.forCause(new SceneLeaseId("lease:service-work-hot"), bootstrap.worldId(), new SettlementServiceWorkSceneCause(work.id()),
                start.support(), new SimInstant(1_001L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(work.workerId(), SceneLease.deterministicEntityId(bootstrap.worldId(), work.workerId()))), java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = FrontierTestActorBodies.present(admitted.prepareSceneLease(lease), lease)
                .transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        FrontierWorldState afterRestart = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(
                hot.transitionSceneLease(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART)));
        assertEquals(SceneLeaseStatus.UNKNOWN_AFTER_RESTART, afterRestart.sceneLeases().get(lease.id()).status());
        assertEquals(SettlementServiceWorkPhase.PREPARED, afterRestart.serviceWorks().get(work.id()).phase(),
                "lost body custody before an input/effect must retain the exact unfinished service stage");
        assertEquals(0, afterRestart.serviceWorks().get(work.id()).inputTraversalCursor(),
                "restart uncertainty must not discard the durable traversal cursor");
        FrontierWorldState reclaimed = afterRestart.transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        SurfaceAnchor next = work.inputTraversal().linearCorridorSurfaces().get(1);
        SettlementServiceWorkTraversalAdvanced advance = new SettlementServiceWorkTraversalAdvanced(work.id(), lease.id(), next.standingBody(), 1,
                ModeledActorBodyFacts.serviceObservation(reclaimed, work, lease.id()));
        var id = advance.execution();
        var future = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(
                id.actorId(), id.activityKind(), id.activityOwnerId(), id.generation() + 1L);
        assertThrows(IllegalArgumentException.class, () -> SettlementServiceWorkProcess.reduceHotTraversalAdvanced(reclaimed, settlement.id(),
                new SettlementServiceWorkTraversalAdvanced(work.id(), lease.id(), next.standingBody(), 1,
                        ModeledActorBodyFacts.serviceObservation(reclaimed, work, lease.id(), future))),
                "matching work and cursor cannot bless a foreign execution generation");
        var foreign = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(
                id.actorId(), id.activityKind(), new SubjectId("service:foreign-owner"), id.generation());
        assertThrows(IllegalArgumentException.class, () -> new SettlementServiceWorkTraversalAdvanced(
                work.id(), lease.id(), next.standingBody(), 1, ModeledActorBodyFacts.serviceObservation(reclaimed, work, lease.id(), foreign)));
        assertEquals(advance, FrontierWorldRuntimeDefinition.payloadCodecs().decode(advance.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(advance)));

        assertThrows(IllegalArgumentException.class, () -> SettlementServiceWorkProcess.reduceHotTraversalAdvanced(reclaimed, settlement.id(), advance),
                "the family receipt cannot install a worker's physical position");
        var inspected = ModeledActorBodyFacts.inspected(reclaimed, work.workerId(), next.standingBody());
        FrontierWorldState advanced = SettlementServiceWorkProcess.reduceHotTraversalAdvanced(inspected, settlement.id(), advance);
        assertEquals(inspected.actorLocations(), advanced.actorLocations());

        assertEquals(1, advanced.serviceWorks().get(work.id()).inputTraversalCursor());
        assertEquals(next.standingBody(), advanced.sceneLeases().get(lease.id()).memberBody(advanced.actorLocations(), work.workerId()));
        assertEquals(next.standingBody(), advanced.actorLocations().get(work.workerId()).body(),
                "common body inspection owns the pose; the service only acknowledges its cursor");
        assertThrows(IllegalArgumentException.class, () -> SettlementServiceWorkProcess.reduceHotTraversalAdvanced(reclaimed, settlement.id(),
                new SettlementServiceWorkTraversalAdvanced(work.id(), lease.id(), next.standingBody(), 2, advance.observation())),
                "an observed arrival may not skip a retained edge");
        var blocked = new SettlementServiceWorkTraversalBlocked(work.id(), lease.id(), start.standingBody(), 1, advance.observation());
        assertEquals(blocked, FrontierWorldRuntimeDefinition.payloadCodecs().decode(blocked.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(blocked)));
        var held = SettlementServiceWorkProcess.reduceHotTraversalBlocked(reclaimed, settlement.id(), blocked);
        assertTrue(held.actorExecutions().actors().get(work.workerId()).current().isEmpty());
        assertEquals(reclaimed.physicalIntents(), held.physicalIntents(),
                "blocking movement cannot erase or rewrite the service's physical-effect obligations");
        assertThrows(IllegalArgumentException.class, () -> SettlementServiceWorkProcess.reduceHotTraversalAdvanced(held, settlement.id(), advance),
                "late movement cannot reactivate a terminal service");
        var base = FrontierWorldRuntimeDefinition.configuration(bootstrap.worldId(), 214L);
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(
                new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(bootstrap.worldId(), reclaimed,
                        new SimInstant(1_001L), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                        base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter()));
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:service-worker-death");
        var death = engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, bootstrap.worldId(),
                engine.checkpoint().revision(), engine.checkpoint().instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied(
                        ActorBodyAuthority.current(reclaimed, work.workerId()), reclaimed.actorLocations().get(work.workerId()).body(),
                        reclaimed.actorLocations().get(work.workerId()).condition().health(), Optional.of(start.standingBody()),
                        Optional.of(SettlementServiceExecutionAuthority.current(reclaimed, work)), "test:service-worker-death")));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, death, death::toString);
        var deadState = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(deadState.actorExecutions().actors().get(work.workerId()).current().isEmpty());
        assertEquals(SettlementServiceWorkPhase.BLOCKED, deadState.serviceWorks().get(work.id()).phase());
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART,
                deadState.physicalIntents().get(work.inputIssueIntentId()).status(),
                "death ends actor authority but retains an explicit physical reconciliation obligation");
    }

    @Test
    void medicHeldEndpointConsumesOnlyAfterWorkAndConfirmsTheExactCell() {
        ReadyEndpoint ready = readyEndpoint(215L);
        long prior = ready.state().infection().get(ready.cell()).value().raw();
        FrontierWorldState running = transition(ready.state(), ready.work().settlementId(), ready.intent(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, Optional.empty());
        DecontaminationObservation observation = new DecontaminationObservation(new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:service-complete"),
                ready.intent().id(), ready.item(), ready.cell(), prior,
                Math.max(0L, prior - ready.state().bootstrap().ruleset().rates().decontaminationReduction().raw()));

        FrontierWorldState complete = transition(running, ready.work().settlementId(), ready.intent(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(observation));

        assertEquals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED,
                complete.physicalIntents().get(ready.intent().id()).status());
        assertFalse(complete.inventory().items().containsKey(ready.item()));
        assertEquals(SettlementServiceWorkPhase.COMPLETED, complete.serviceWorks().get(ready.work().id()).phase());
        assertTrue(complete.actorExecutions().actors().get(ready.work().workerId()).current().isEmpty(),
                "confirmed endpoint retires service authority independently of body draining");
        assertEquals(observation.remainingRaw(), complete.infection().get(ready.cell()).value().raw());
        var lease = complete.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isServiceWork)
                .findFirst().orElseThrow();
        var unknown = complete.transitionSceneLease(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        unknown = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unknown));
        assertEquals(SceneLeaseStatus.DRAINING, FrontierSceneBehaviors.recoveredStatus(unknown, unknown.sceneLeases().get(lease.id())));
        var draining = unknown.transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING);
        var worker = draining.actorLocations().get(ready.work().workerId());
        var closed = draining.releaseSceneLease(lease.id(), List.of(new SceneMemberPosition(ready.work().workerId(), worker.body(), worker.condition().health())));
        assertEquals(SceneLeaseStatus.CLOSED, closed.sceneLeases().get(lease.id()).status());
        assertEquals(complete.inventory(), closed.inventory());
        assertEquals(complete.infection(), closed.infection());
        assertEquals(complete.physicalObservations(), closed.physicalObservations());
    }

    @Test
    void unknownEndpointRetainsTheExactMedicAndRecoversOnlyByObservedPostcondition() {
        ReadyEndpoint ready = readyEndpoint(216L);
        FrontierWorldState unknown = transition(ready.state(), ready.work().settlementId(), ready.intent(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unknown));
        assertEquals(SettlementServiceWorkPhase.UNKNOWN_AFTER_RESTART, recovered.serviceWorks().get(ready.work().id()).phase());
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART,
                recovered.physicalIntents().get(ready.intent().id()).status());

        SceneLeaseId leaseId = recovered.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isServiceWork)
                .map(SceneLease::id).findFirst().orElseThrow();
        recovered = recovered.transitionSceneLease(leaseId, SceneLeaseStatus.HOT);

        long prior = recovered.infection().get(ready.cell()).value().raw();
        DecontaminationObservation observation = new DecontaminationObservation(new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:service-recovered"),
                ready.intent().id(), ready.item(), ready.cell(), prior,
                Math.max(0L, prior - recovered.bootstrap().ruleset().rates().decontaminationReduction().raw()));
        FrontierWorldState complete = transition(recovered, ready.work().settlementId(), ready.intent(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(observation));

        assertEquals(SettlementServiceWorkPhase.COMPLETED, complete.serviceWorks().get(ready.work().id()).phase());
        assertFalse(complete.inventory().items().containsKey(ready.item()));
    }

    private static ReadyEndpoint readyEndpoint(long seed) {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:service-ready-" + seed), seed);
        Settlement settlement = settlement(bootstrap, "settlement:9"); InfectionCell cell = treatmentCell(bootstrap, settlement);
        SubjectId depot = FrontierWorldState.depotId(settlement.id()), item = new SubjectId("item:service-ready-" + seed);
        FrontierWorldState source = taskState(bootstrap, settlement, cell).withInventory(FrontierWorldState.initial(bootstrap).inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(item, settlement.id(), DecontaminationPolicy.REAGENT, 1, new InventoryCustody.ContainerSlot(depot, 1))));
        SettlementServiceWorkStarted started = SettlementServiceWorkProcess.planDecontamination(source, SettlementServiceWorkProcess.scan(1, 1_000L)).stream()
                .map(ProposedEvent::payload).filter(SettlementServiceWorkStarted.class::isInstance).map(SettlementServiceWorkStarted.class::cast).findFirst().orElseThrow();
        FrontierWorldState admitted = SettlementServiceWorkProcess.reduceStarted(
                source.withStrategicPlans(source.strategicPlans().transitionTask(started.taskId(), StrategicTaskStatus.ACTIVE)), settlement.id(), started);
        SettlementServiceWork work = admitted.serviceWorks().get(started.work().id());
        SceneLeaseId leaseId = new SceneLeaseId("lease:service-ready-" + seed);
        SurfaceAnchor initialSurface = FrontierSettlementServiceWorkSceneSupport.currentSurface(work);
        SceneLease lease = SceneLease.forCause(leaseId, bootstrap.worldId(), new SettlementServiceWorkSceneCause(work.id()), initialSurface.support(),
                new SimInstant(1_001L), 1L, SceneLeaseStatus.PREPARED, List.of(new SceneMember(work.workerId(),
                SceneLease.deterministicEntityId(bootstrap.worldId(), leaseId, work.workerId()))), java.util.Set.of(), Optional.empty());
        FrontierWorldState ready = ModeledActorBodyFacts.present(admitted.prepareSceneLease(lease), work.workerId())
                .transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        while (work.inputTraversalCursor() < work.inputTraversal().linearCorridorSurfaces().size() - 1) {
            int next = work.inputTraversalCursor() + 1;
            ready = ModeledActorBodyFacts.inspected(ready, work.workerId(), work.inputTraversal().linearCorridorSurfaces().get(next).standingBody());
            ready = SettlementServiceWorkProcess.reduceHotTraversalAdvanced(ready, settlement.id(),
                    new SettlementServiceWorkTraversalAdvanced(work.id(), leaseId, work.inputTraversal().linearCorridorSurfaces().get(next).standingBody(), next,
                            ModeledActorBodyFacts.serviceObservation(ready, work, leaseId)));
            work = ready.serviceWorks().get(work.id());
        }
        SettlementServiceInputIssueObservation inputReceipt = new SettlementServiceInputIssueObservation(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:service-input-" + seed), work.inputIssueIntentId(),
                work.id(), work.workerId(), item, work.inputSource());
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents = new LinkedHashMap<>(ready.physicalIntents());
        intents.put(work.inputIssueIntentId(), intents.get(work.inputIssueIntentId()).withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED,
                Optional.of(inputReceipt.id())));
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(ready.physicalObservations());
        observations.put(inputReceipt.id(), inputReceipt);
        SettlementServiceWork issued = work.withInputIssued();
        Map<SubjectId, SettlementServiceWork> issuedWorks = new LinkedHashMap<>(ready.serviceWorks()); issuedWorks.put(issued.id(), issued);
        ready = ready.withChanges(FrontierWorldStateUpdate.begin().serviceWorks(issuedWorks).physicalIntents(intents).physicalObservations(observations)
                .inventory(ready.inventory().moveObservedItem(item, issued.inputSource(), new InventoryCustody.Actor(issued.workerId()))));
        work = ready.serviceWorks().get(issued.id());
        while (work.workTraversalCursor() < work.workTraversal().linearCorridorSurfaces().size() - 1) {
            int next = work.workTraversalCursor() + 1;
            ready = ModeledActorBodyFacts.inspected(ready, work.workerId(), work.workTraversal().linearCorridorSurfaces().get(next).standingBody());
            ready = SettlementServiceWorkProcess.reduceHotTraversalAdvanced(ready, settlement.id(),
                    new SettlementServiceWorkTraversalAdvanced(work.id(), leaseId, work.workTraversal().linearCorridorSurfaces().get(next).standingBody(), next,
                            ModeledActorBodyFacts.serviceObservation(ready, work, leaseId)));
            work = ready.serviceWorks().get(work.id());
        }
        SettlementServiceWork effectReady = work.withPhase(SettlementServiceWorkPhase.EFFECT_READY, 0);
        Map<SubjectId, SettlementServiceWork> effectReadyWorks = new LinkedHashMap<>(ready.serviceWorks()); effectReadyWorks.put(effectReady.id(), effectReady);
        ready = ready.withChanges(FrontierWorldStateUpdate.begin().serviceWorks(effectReadyWorks));
        var endpoint = ready.physicalIntents().get(effectReady.endpointIntentId());
        ready = ready.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                FencedRecoveryPhysicalIntentSupport.prepared(ready.fencedRecovery(), endpoint, FencedRecoveryAsset.EFFECT)));
        return new ReadyEndpoint(ready, effectReady, endpoint, item, cell);
    }

    private static FrontierWorldState transition(FrontierWorldState state, SubjectId settlement,
                                                 io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                 io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus status,
                                                 Optional<PhysicalEffectObservation> observation) {
        return PhysicalIntentLifecycleFixture.transition(state, settlement, intent, status, observation);
    }

    private record ReadyEndpoint(FrontierWorldState state, SettlementServiceWork work,
                                 io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent, SubjectId item, InfectionCell cell) { }

    private static FrontierWorldState taskState(FrontierBootstrap bootstrap, Settlement settlement, InfectionCell cell) {
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap).withInfection(cell, new FixedRatio(new FixedScalar(750_000L)));
        String suffix = settlement.id().value().replace(':', '-');
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:" + suffix), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, Optional.of(cell), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:" + suffix), objective.id(), settlement.id(),
                StrategicTaskKind.DECONTAMINATE_INFECTION_CELL, Optional.of(cell), List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY,
                StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT), List.of(), StrategicTaskStatus.PENDING);
        return initial.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
    }

    private static Settlement settlement(FrontierBootstrap bootstrap, String id) {
        return bootstrap.settlements().stream().filter(value -> value.id().value().equals(id)).findFirst().orElseThrow();
    }

    private static SettlementStructure infirmary(Settlement settlement) {
        return settlement.structures().stream().filter(value -> value.kind() == StructureKind.INFIRMARY).findFirst().orElseThrow();
    }

    private static InfectionCell treatmentCell(FrontierBootstrap bootstrap, Settlement settlement) {
        SettlementStructure infirmary = infirmary(settlement);
        for (int radius = 4; radius <= 32; radius += 4) for (int dx = -radius; dx <= radius; dx += 4) {
            for (int dz = -radius; dz <= radius; dz += 4) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                InfectionCell cell = InfectionCell.at(infirmary.anchor().offset(dx, 0, dz));
                try {
                    if (!InfectionTreatmentWorksite.candidates(bootstrap, cell).isEmpty()) return cell;
                } catch (IllegalArgumentException ignored) {
                    // The planner, not the fixture, decides which immutable worksite is usable.
                }
            }
        }
        throw new IllegalStateException("fixture has no treatment cell near its infirmary");
    }
}
