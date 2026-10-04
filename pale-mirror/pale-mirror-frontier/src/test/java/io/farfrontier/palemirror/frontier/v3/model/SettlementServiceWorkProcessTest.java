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
        FencedRecoveryPhysicalIntentSupport.requirePreparedExecutionAuthority(admitted.fencedRecovery(),
                started.inputIssueIntent(), FencedRecoveryAsset.EFFECT);
        FencedRecoveryPhysicalIntentSupport.requirePreparedExecutionAuthority(admitted.fencedRecovery(),
                started.endpointIntent(), FencedRecoveryAsset.EFFECT);
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

    @Test
    void offStationDepartureRetainsWorkAndRejoinsAfterSnapshotWithoutRestoringOldPose() {
        ReadyEndpoint fixture = readyEndpoint(217L);
        var work = fixture.work().withPhase(SettlementServiceWorkPhase.WORKING, 7);
        var state = fixture.state().withChanges(FrontierWorldStateUpdate.begin().serviceWorks(Map.of(work.id(), work)));
        var knowledge = SettlementServiceJourneyKnowledge.view(state, work);
        var origin = knowledge.supportAt(work.workStation().x() + 3, work.workStation().z());
        var away = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(work.id(), work.workerId(),
                1L, 1L, List.of(origin), TraversalCapability.PEDESTRIAN,
                io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
        var path = knowledge.path(work.workStation(), away);
        assertTrue(path.size() > 1, "departure fixture must leave the exact station");
        origin = path.get(1);
        var lease = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isServiceWork).findFirst().orElseThrow();
        var oldObservation = ModeledActorBodyFacts.serviceObservation(state, work, lease.id());
        state = ModeledActorBodyFacts.inspected(state, work.workerId(), origin.standingBody());
        state = state.transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING);
        var location = state.actorLocations().get(work.workerId());
        state = state.releaseSceneLease(lease.id(), List.of(new SceneMemberPosition(work.workerId(), location.body(), location.condition().health())));
        assertEquals(origin.support(), FrontierSettlementServiceWorkSceneSupport.candidate(state, work).orElseThrow().handoffPosition(),
                "scope return cannot require physical absence or a fabricated old cursor pose");
        var reopened = SettlementServiceJourneyKnowledge.atScopeAdmission(state, work);
        assertEquals(state.actorLocations(), reopened.actorLocations());
        assertEquals(state.fencedRecovery(), reopened.fencedRecovery());
        assertEquals(work.workStation(), reopened.serviceWorks().get(work.id()).spatial().approach().orElseThrow().target());
        var departed = ModeledActorBodyFacts.unloaded(state, work.workerId());
        var retained = departed.serviceWorks().get(work.id());
        assertEquals(work.inputTraversal(), retained.inputTraversal());
        assertEquals(work.workTraversal(), retained.workTraversal());
        assertEquals(7, retained.completedWorkTicks());
        assertEquals(fixture.state().inventory(), departed.inventory());
        assertEquals(fixture.state().physicalIntents(), departed.physicalIntents());
        assertEquals(origin, retained.spatial().approach().orElseThrow().current());
        assertEquals(work.workStation(), retained.spatial().approach().orElseThrow().target());

        var restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(departed));
        assertEquals(departed, restored);
        var candidate = FrontierSettlementServiceWorkSceneSupport.candidate(restored, retained).orElseThrow();
        assertEquals(origin.support(), candidate.handoffPosition());
        var scope = SceneLease.forCause(new SceneLeaseId("lease:service-rejoin"), restored.bootstrap().worldId(),
                new SettlementServiceWorkSceneCause(work.id()), candidate.handoffPosition(), new SimInstant(2_000L), 2L,
                SceneLeaseStatus.PREPARED, List.of(new SceneMember(work.workerId(),
                        SceneLease.deterministicEntityId(restored.bootstrap().worldId(), work.workerId()))), java.util.Set.of(), Optional.empty());
        var hot = ModeledActorBodyFacts.present(restored.prepareSceneLease(scope), work.workerId())
                .transitionSceneLease(scope.id(), SceneLeaseStatus.HOT);
        var currentObservation = ModeledActorBodyFacts.serviceObservation(hot, retained, scope.id());
        var arrived = ModeledActorBodyFacts.inspected(hot, work.workerId(), work.workStation().standingBody());
        var progress = new SettlementServiceWorkProgressed(work.id(), scope.id(), work.workStation().standingBody(),
                SettlementServiceWorkPhase.WORKING, 8, currentObservation);
        assertThrows(IllegalArgumentException.class, () -> SettlementServiceWorkProcess.reduceHotProgressed(arrived,
                work.settlementId(), new SettlementServiceWorkProgressed(work.id(), scope.id(), work.workStation().standingBody(),
                        SettlementServiceWorkPhase.WORKING, 8, oldObservation)));
        var staleSpatial = new SettlementServiceWorkObservation(currentObservation.authority(), retained.phase(),
                retained.inputTraversalCursor(), retained.workTraversalCursor(), retained.completedWorkTicks(),
                retained.spatial().revision() - 1);
        assertThrows(IllegalArgumentException.class, () -> SettlementServiceWorkProcess.reduceHotProgressed(arrived,
                work.settlementId(), new SettlementServiceWorkProgressed(work.id(), scope.id(), work.workStation().standingBody(),
                        SettlementServiceWorkPhase.WORKING, 8, staleSpatial)));
        var continued = SettlementServiceWorkProcess.reduceHotProgressed(arrived, work.settlementId(), progress);
        assertEquals(arrived.actorLocations(), continued.actorLocations());
        assertFalse(continued.serviceWorks().get(work.id()).spatial().pending());
        assertEquals(8, continued.serviceWorks().get(work.id()).completedWorkTicks());
        assertThrows(IllegalArgumentException.class, () -> SettlementServiceWorkProcess.reduceHotProgressed(continued, work.settlementId(), progress));
    }

    @Test
    void pendingInputDefersDuringDepartureButAppliedReceiptKeepsItsExactResourceOwner() {
        var input = readyInput(219L); var work = input.work();
        var intent = input.state().physicalIntents().get(work.inputIssueIntentId());
        var running = transition(input.state(), work.settlementId(), intent,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, Optional.empty());
        var path = work.workTraversal().linearCorridorSurfaces();
        assertTrue(path.size() > 1, "fixture must depart the input station");
        var observed = ModeledActorBodyFacts.inspected(running, work.workerId(), path.get(1).standingBody());
        var departed = ModeledActorBodyFacts.unloaded(observed, work.workerId());
        assertEquals(SettlementServiceInputIssueStateSupport.ExecutionEligibility.READY,
                SettlementServiceInputIssueStateSupport.executionEligibility(departed, departed.physicalIntents().get(intent.id())),
                "a started effect stays inspectable independently of the worker's position");
        assertEquals(SettlementServiceInputIssueStateSupport.ExecutionEligibility.DEFERRED,
                SettlementServiceInputIssueStateSupport.executionEligibility(departed, intent),
                "an unstarted take must wait for its retained source station");
        assertThrows(IllegalArgumentException.class, () -> SettlementServiceInputIssueStateSupport.validateIntent(departed, intent),
                "saved departure cannot authorize a remote new take");
        assertEquals(input.state().inventory(), departed.inventory());
        var restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(departed));
        var receipt = new SettlementServiceInputIssueObservation(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:service-departed-input"),
                intent.id(), work.id(), work.workerId(), work.inputItemId(), work.inputSource());
        var uncertain = transition(running, work.settlementId(), intent,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        assertEquals(SettlementServiceWorkPhase.UNKNOWN_AFTER_RESTART, uncertain.serviceWorks().get(work.id()).phase(),
                "uncertainty must update the input owner's phase atomically with the effect");
        assertEquals(uncertain, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(uncertain)));
        var resolved = transition(uncertain, work.settlementId(), intent,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(SettlementServiceWorkPhase.APPROACH_WORK, resolved.serviceWorks().get(work.id()).phase());
        assertEquals(uncertain.actorLocations(), resolved.actorLocations());

        var location = observed.actorLocations().get(work.workerId());
        var death = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied(
                ActorBodyAuthority.current(observed, work.workerId()), location.body(), location.condition().health(),
                Optional.of(location.body()), observed.actorExecutions().actors().get(work.workerId()).current(),
                "test:applied-service-input-death");
        var dead = ActorBodyAuthority.died(observed, death, FrontierActorDeathConsequences.INSTANCE, 1_004L);
        var retainedIntent = dead.physicalIntents().get(intent.id());
        assertEquals(SettlementServiceWorkPhase.BLOCKED, dead.serviceWorks().get(work.id()).phase());
        assertEquals(SettlementServiceInputIssueStateSupport.ExecutionEligibility.READY,
                SettlementServiceInputIssueStateSupport.executionEligibility(dead, retainedIntent));
        assertThrows(IllegalArgumentException.class, () -> SettlementServiceInputIssueStateSupport.validateIntent(dead, retainedIntent));
        assertThrows(IllegalArgumentException.class, () -> SettlementServiceInputIssueStateSupport.validateUnappliedRetry(dead, retainedIntent));
        var settled = transition(dead, work.settlementId(), retainedIntent,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(dead.serviceWorks(), settled.serviceWorks(), "late resource settlement cannot revive a retired job");
        assertEquals(dead.actorExecutions(), settled.actorExecutions());
        assertEquals(dead.actorLocations(), settled.actorLocations());
        assertEquals(new InventoryCustody.Actor(work.workerId()), settled.inventory().items().get(work.inputItemId()).custody());
        assertEquals(settled, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(settled)));

        var confirmed = transition(restored, work.settlementId(), intent,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(restored.actorLocations(), confirmed.actorLocations(), "resource settlement cannot restore station pose");
        assertEquals(new InventoryCustody.Actor(work.workerId()), confirmed.inventory().items().get(work.inputItemId()).custody());
        assertEquals(SettlementServiceWorkPhase.APPROACH_WORK, confirmed.serviceWorks().get(work.id()).phase());
        assertEquals(path.get(1), confirmed.serviceWorks().get(work.id()).spatial().approach().orElseThrow().current());
        assertEquals(path.get(1), confirmed.serviceWorks().get(work.id()).spatial().approach().orElseThrow().target());
        assertEquals(confirmed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(confirmed)));
        assertThrows(IllegalArgumentException.class, () -> transition(confirmed, work.settlementId(), intent,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)));
    }

    @Test
    void serviceDeathWithoutSceneRetiresItsExactExecutionAndKeepsResourceSettlement() {
        var input = readyInput(220L); var work = input.work();
        var state = input.state().withChanges(FrontierWorldStateUpdate.begin().sceneLeases(Map.of()));
        var intent = state.physicalIntents().get(work.inputIssueIntentId());
        state = transition(state, work.settlementId(), intent,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, Optional.empty());
        var location = state.actorLocations().get(work.workerId());
        var death = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied(
                ActorBodyAuthority.current(state, work.workerId()), location.body(), location.condition().health(),
                Optional.of(location.body()), Optional.of(SettlementServiceExecutionAuthority.current(state, work)),
                "test:service-death-without-scene");
        var dead = ActorBodyAuthority.died(state, death, FrontierActorDeathConsequences.INSTANCE, 1_004L);
        assertEquals(SettlementServiceWorkPhase.BLOCKED, dead.serviceWorks().get(work.id()).phase());
        assertTrue(dead.actorExecutions().actors().get(work.workerId()).current().isEmpty());
        var receipt = new SettlementServiceInputIssueObservation(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:service-death-no-scene"),
                intent.id(), work.id(), work.workerId(), work.inputItemId(), work.inputSource());
        var settled = transition(dead, work.settlementId(), dead.physicalIntents().get(intent.id()),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(dead.actorLocations(), settled.actorLocations());
        assertEquals(dead.actorExecutions(), settled.actorExecutions());
        assertEquals(dead.serviceWorks(), settled.serviceWorks());
        assertEquals(new InventoryCustody.Actor(work.workerId()), settled.inventory().items().get(work.inputItemId()).custody());
        assertEquals(settled, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(settled)));
    }

    @Test
    void alreadyAppliedEndpointSettlesAfterDeathWithoutSceneOrResurrectingWork() {
        var ready = readyEndpoint(221L); var work = ready.work();
        var running = transition(ready.state(), work.settlementId(), ready.intent(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, Optional.empty());
        var noScene = running.withChanges(FrontierWorldStateUpdate.begin().sceneLeases(Map.of()));
        var location = noScene.actorLocations().get(work.workerId());
        var dead = ActorBodyAuthority.died(noScene, new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied(
                ActorBodyAuthority.current(noScene, work.workerId()), location.body(), location.condition().health(),
                Optional.of(location.body()), Optional.of(SettlementServiceExecutionAuthority.current(noScene, work)),
                "test:service-endpoint-death"), FrontierActorDeathConsequences.INSTANCE, 1_004L);
        var restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(dead));
        var intent = restored.physicalIntents().get(work.endpointIntentId());
        assertEquals(SettlementServiceDecontaminationStateSupport.ExecutionEligibility.READY,
                SettlementServiceDecontaminationStateSupport.executionEligibility(restored, intent));
        var prior = restored.infection().get(ready.cell()).value().raw();
        var receipt = new DecontaminationObservation(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:service-endpoint-after-death"),
                intent.id(), work.inputItemId(), ready.cell(), prior,
                Math.max(0L, prior - restored.bootstrap().ruleset().rates().decontaminationReduction().raw()));
        var settled = transition(restored, work.settlementId(), intent,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(restored.serviceWorks(), settled.serviceWorks());
        assertEquals(restored.actorExecutions(), settled.actorExecutions());
        assertEquals(restored.actorLocations(), settled.actorLocations());
        assertFalse(settled.inventory().items().containsKey(work.inputItemId()));
        assertEquals(receipt.remainingRaw(), settled.infection().get(ready.cell()).value().raw());
        assertEquals(settled, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(settled)));
        assertThrows(IllegalArgumentException.class, () -> transition(settled, work.settlementId(), intent,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)));
    }

    @Test
    void abandoningUnperformedInputNeedsTheRetiredDeadOwnerNotAnActiveWorker() {
        var input = readyInput(222L); var work = input.work();
        var intent = input.state().physicalIntents().get(work.inputIssueIntentId());
        var running = transition(input.state(), work.settlementId(), intent,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> transition(running, work.settlementId(), intent,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFLICTED, Optional.empty()));
        var location = running.actorLocations().get(work.workerId());
        var dead = ActorBodyAuthority.died(running, new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied(
                ActorBodyAuthority.current(running, work.workerId()), location.body(), location.condition().health(),
                Optional.of(location.body()), Optional.of(SettlementServiceExecutionAuthority.current(running, work)),
                "test:service-unapplied-death"), FrontierActorDeathConsequences.INSTANCE, 1_004L);
        var abandoned = transition(dead, work.settlementId(), dead.physicalIntents().get(intent.id()),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFLICTED, Optional.empty());
        assertEquals(dead.inventory(), abandoned.inventory());
        assertEquals(dead.actorExecutions(), abandoned.actorExecutions());
        assertEquals(dead.serviceWorks(), abandoned.serviceWorks());
        assertFalse(abandoned.physicalIntents().get(intent.id()).postconditionObservationId().isPresent());
        assertEquals(abandoned, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(abandoned)));
    }

    @Test
    void treatmentStationsUseActualKnownSupportNotFeetAir() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:service-support"), 218L);
        var settlement = settlement(bootstrap, "settlement:9");
        var cell = treatmentCell(bootstrap, settlement);
        var known = KnownPedestrianGround.forBootstrap(bootstrap);
        for (var station : InfectionTreatmentWorksite.candidates(bootstrap, cell))
            assertEquals(known.at(station.x(), station.z()), station);
    }

    private static ReadyInput readyInput(long seed) {
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
        return new ReadyInput(ready, work, leaseId, item, cell);
    }

    private static ReadyEndpoint readyEndpoint(long seed) {
        var input = readyInput(seed);
        var ready = input.state(); var work = input.work(); var item = input.item(); var cell = input.cell();
        var leaseId = input.leaseId();
        var settlement = settlement(ready.bootstrap(), work.settlementId().value());
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
        return new ReadyEndpoint(ready, effectReady, endpoint, item, cell);
    }

    private static FrontierWorldState transition(FrontierWorldState state, SubjectId settlement,
                                                 io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                 io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus status,
                                                 Optional<PhysicalEffectObservation> observation) {
        return PhysicalIntentLifecycleFixture.transition(state, settlement, intent, status, observation);
    }

    private record ReadyInput(FrontierWorldState state, SettlementServiceWork work, SceneLeaseId leaseId,
                              SubjectId item, InfectionCell cell) { }

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
